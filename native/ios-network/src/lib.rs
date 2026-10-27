//! App-contained DoH + ECH. No listener, root certificate installation, or TLS trust override.
mod dns;

use base64::{engine::general_purpose::STANDARD, Engine as _};
use bytes::Bytes;
use http_body_util::{BodyExt, Full, Limited};
use hyper::{header, Request, Uri};
use hyper_util::{
    client::legacy::{
        connect::{Connected, Connection},
        Client,
    },
    rt::{TokioExecutor, TokioIo},
};
use rustls::{
    client::{EchConfig, EchMode},
    pki_types::ServerName,
    ClientConfig, RootCertStore,
};
use serde::{Deserialize, Serialize};
use std::{
    collections::HashMap,
    ffi::{CStr, CString},
    future::Future,
    io::{self, Read},
    net::SocketAddr,
    os::raw::c_char,
    pin::Pin,
    sync::{
        atomic::{AtomicU64, Ordering},
        Arc, Mutex, OnceLock,
    },
    task::{Context, Poll},
    time::{Duration, Instant},
};
use tokio::{
    io::{AsyncRead, AsyncWrite, ReadBuf},
    net::TcpStream,
    runtime::Runtime,
    task::AbortHandle,
};
use tokio_rustls::{client::TlsStream, TlsConnector};
use tower_service::Service;

type BoxError = Box<dyn std::error::Error + Send + Sync>;
const MAX_BODY: usize = 32 * 1024 * 1024;
const TIMEOUT: Duration = Duration::from_secs(35);

#[derive(Clone, Deserialize, Serialize, PartialEq, Eq)]
struct Resolver {
    url: String,
    #[serde(default)]
    bootstrap: Vec<String>,
}
#[derive(Clone, Deserialize, Serialize, PartialEq, Eq)]
struct Settings {
    resolvers: Vec<Resolver>,
    #[serde(default)]
    ipv6: bool,
}
#[derive(Deserialize)]
struct Input {
    url: String,
    method: String,
    headers: Vec<(String, String)>,
    #[serde(default)]
    body: String,
    settings: Settings,
    #[serde(default = "default_body_limit")]
    max_body: usize,
}
fn default_body_limit() -> usize {
    MAX_BODY
}
#[derive(Clone, Debug, Default, Serialize)]
struct Facts {
    stage: String,
    ech: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    ip: Option<String>,
}
#[derive(Debug, Serialize)]
struct Output {
    code: u16,
    headers: Vec<(String, String)>,
    body: String,
    error: Option<String>,
    facts: Facts,
}
#[derive(Debug)]
struct Failure {
    stage: &'static str,
    code: &'static str,
}
impl std::fmt::Display for Failure {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}:{}", self.stage, self.code)
    }
}
impl std::error::Error for Failure {}
fn fail(stage: &'static str, code: &'static str) -> BoxError {
    Box::new(Failure { stage, code })
}

#[derive(Clone)]
struct Resolved {
    addrs: Vec<SocketAddr>,
    tls: Arc<ClientConfig>,
    expires: Instant,
}
struct ResolverClient {
    http: reqwest::Client,
    url: String,
}
#[derive(Clone)]
struct Connector {
    resolvers: Arc<Vec<ResolverClient>>,
    cache: Arc<tokio::sync::Mutex<HashMap<String, Resolved>>>,
    ipv6: bool,
}

impl Connector {
    fn new(settings: &Settings) -> Result<Self, BoxError> {
        if settings.resolvers.is_empty() || settings.resolvers.len() > 8 {
            return Err(fail("CONFIG", "invalid_resolvers"));
        }
        let mut resolvers = Vec::new();
        for resolver in &settings.resolvers {
            let url = reqwest::Url::parse(&resolver.url)
                .map_err(|_| fail("CONFIG", "invalid_resolver_url"))?;
            if url.scheme() != "https"
                || url.host_str().is_none()
                || !url.username().is_empty()
                || url.password().is_some()
            {
                return Err(fail("CONFIG", "invalid_resolver_url"));
            }
            let mut builder = reqwest::Client::builder()
                .no_proxy()
                .https_only(true)
                .redirect(reqwest::redirect::Policy::none())
                .timeout(Duration::from_secs(10));
            if !resolver.bootstrap.is_empty() {
                let port = url.port_or_known_default().unwrap_or(443);
                let addrs = resolver
                    .bootstrap
                    .iter()
                    .map(|ip| ip.parse().map(|ip| SocketAddr::new(ip, port)))
                    .collect::<Result<Vec<_>, _>>()
                    .map_err(|_| fail("CONFIG", "invalid_bootstrap"))?;
                builder = builder.resolve_to_addrs(url.host_str().unwrap(), &addrs);
            }
            let http = builder
                .build()
                .map_err(|_| fail("CONFIG", "resolver_client"))?;
            resolvers.push(ResolverClient {
                http,
                url: resolver.url.clone(),
            });
        }
        Ok(Self {
            resolvers: Arc::new(resolvers),
            cache: Arc::new(tokio::sync::Mutex::new(HashMap::new())),
            ipv6: settings.ipv6,
        })
    }

    async fn query(
        &self,
        resolver: &ResolverClient,
        host: &str,
        kind: u16,
    ) -> Result<dns::Answer, BoxError> {
        let mut name = host.to_string();
        for _ in 0..5 {
            let wire = dns::query(&name, kind).map_err(|e| fail("DNS", e))?;
            let mut response = resolver
                .http
                .post(&resolver.url)
                .header("Content-Type", "application/dns-message")
                .header("Accept", "application/dns-message")
                .body(wire)
                .send()
                .await
                .map_err(|_| fail("DNS", "resolver_connection"))?;
            if !response.status().is_success() {
                return Err(fail("DNS", "resolver_http_status"));
            }
            let mut bytes = Vec::new();
            while let Some(chunk) = response
                .chunk()
                .await
                .map_err(|_| fail("DNS", "resolver_body"))?
            {
                if bytes.len() + chunk.len() > 65535 {
                    return Err(fail("DNS", "resolver_body_too_large"));
                }
                bytes.extend(chunk);
            }
            let answer = dns::parse(&bytes, &name, kind).map_err(|e| fail("DNS", e))?;
            if answer.ips.is_empty() && answer.ech.is_none() {
                if let Some(alias) = &answer.alias {
                    name = alias.clone();
                    continue;
                }
            }
            return Ok(answer);
        }
        Err(fail("DNS", "alias_limit"))
    }

    async fn resolve(&self, host: &str, port: u16) -> Result<Resolved, BoxError> {
        let key = format!("{host}:{port}");
        if let Some(cached) = self
            .cache
            .lock()
            .await
            .get(&key)
            .filter(|e| e.expires > Instant::now())
            .cloned()
        {
            return Ok(cached);
        }
        let mut last = fail("DNS", "no_address");
        for resolver in self.resolvers.iter() {
            let a = self.query(resolver, host, 1);
            let https = self.query(resolver, host, 65);
            let aaaa = async {
                if self.ipv6 {
                    self.query(resolver, host, 28).await
                } else {
                    Ok(dns::Answer::default())
                }
            };
            let (a, aaaa, https) = tokio::join!(a, aaaa, https);
            // Keep each provider's IPs and ECH config together. A failed HTTPS lookup is observable,
            // never silently replaced by another provider's unencrypted answer.
            let https = match https {
                Ok(v) => v,
                Err(e) => {
                    last = e;
                    continue;
                }
            };
            let mut ips = Vec::new();
            let mut ttl = https.ttl;
            for result in [a, aaaa].into_iter().flatten() {
                ips.extend(result.ips);
                if result.ttl > 0 {
                    ttl = ttl.min(result.ttl);
                }
            }
            ips.dedup();
            if ips.is_empty() {
                last = fail("DNS", "no_address");
                continue;
            }
            let tls = tls_config(https.ech)?;
            let resolved = Resolved {
                addrs: ips
                    .into_iter()
                    .map(|ip| SocketAddr::new(ip, port))
                    .collect(),
                tls,
                expires: Instant::now() + Duration::from_secs(ttl.clamp(1, 3600) as u64),
            };
            let mut cache = self.cache.lock().await;
            cache.retain(|_, entry| entry.expires > Instant::now());
            if cache.len() >= 128 {
                cache.clear();
            }
            cache.insert(key, resolved.clone());
            return Ok(resolved);
        }
        Err(last)
    }

    async fn connect(&self, uri: Uri) -> Result<TokioIo<Stream>, BoxError> {
        if uri.scheme_str() != Some("https") {
            return Err(fail("CONFIG", "https_required"));
        }
        let host = uri.host().ok_or_else(|| fail("CONFIG", "invalid_host"))?;
        let port = uri.port_u16().unwrap_or(443);
        let resolved = self.resolve(host, port).await?;
        let server_name = ServerName::try_from(host.to_string())
            .map_err(|_| fail("TLS", "invalid_server_name"))?;
        let connector = TlsConnector::from(resolved.tls);
        let mut last = fail("TCP", "connection_failed");
        // Race staggered candidates: a blackholed IPv6/IPv4 path must not hold the login for 35s.
        let mut attempts = tokio::task::JoinSet::new();
        for (index, addr) in resolved.addrs.into_iter().take(8).enumerate() {
            let connector = connector.clone();
            let server_name = server_name.clone();
            attempts.spawn(async move {
                tokio::time::sleep(Duration::from_millis(index as u64 * 250)).await;
                let tcp = tokio::time::timeout(Duration::from_secs(7), TcpStream::connect(addr))
                    .await
                    .map_err(|_| fail("TCP", "timeout"))?
                    .map_err(|_| fail("TCP", "connection_failed"))?;
                let _ = tcp.set_nodelay(true);
                let tls = tokio::time::timeout(
                    Duration::from_secs(10),
                    connector.connect(server_name, tcp),
                )
                .await
                .map_err(|_| fail("TLS", "timeout"))?
                .map_err(|e| {
                    let text = e.to_string().to_ascii_lowercase();
                    fail(
                        "TLS",
                        if text.contains("certificate") {
                            "certificate_rejected"
                        } else if text.contains("ech") {
                            "ech_rejected"
                        } else {
                            "handshake_failed"
                        },
                    )
                })?;
                let facts = Facts {
                    stage: "HTTP".into(),
                    ech: format!("{:?}", tls.get_ref().1.ech_status()),
                    ip: Some(addr.ip().to_string()),
                };
                Ok::<_, BoxError>(Stream { tls, facts })
            });
        }
        while let Some(result) = attempts.join_next().await {
            match result {
                Ok(Ok(stream)) => {
                    attempts.abort_all();
                    return Ok(TokioIo::new(stream));
                }
                Ok(Err(e)) => last = e,
                Err(_) => last = fail("TCP", "connection_task"),
            }
        }
        Err(last)
    }
}

fn tls_config(ech: Option<Vec<u8>>) -> Result<Arc<ClientConfig>, BoxError> {
    let provider = Arc::new(rustls::crypto::aws_lc_rs::default_provider());
    let builder = ClientConfig::builder_with_provider(provider);
    let builder = if let Some(ech) = ech {
        let config = EchConfig::new(
            ech.into(),
            rustls::crypto::aws_lc_rs::hpke::ALL_SUPPORTED_SUITES,
        )
        .map_err(|_| fail("ECH_CONFIG", "unsupported_or_invalid"))?;
        builder
            .with_ech(EchMode::Enable(config))
            .map_err(|_| fail("ECH_CONFIG", "unsupported_or_invalid"))?
    } else {
        builder
            .with_safe_default_protocol_versions()
            .map_err(|_| fail("TLS", "configuration"))?
    };
    let mut roots = RootCertStore::empty();
    roots.extend(webpki_roots::TLS_SERVER_ROOTS.iter().cloned());
    let mut config = builder.with_root_certificates(roots).with_no_client_auth();
    config.alpn_protocols = vec![b"h2".to_vec(), b"http/1.1".to_vec()];
    Ok(Arc::new(config))
}

struct Stream {
    tls: TlsStream<TcpStream>,
    facts: Facts,
}
impl Connection for Stream {
    fn connected(&self) -> Connected {
        let c = Connected::new().extra(self.facts.clone());
        if self.tls.get_ref().1.alpn_protocol() == Some(b"h2") {
            c.negotiated_h2()
        } else {
            c
        }
    }
}
impl AsyncRead for Stream {
    fn poll_read(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &mut ReadBuf<'_>,
    ) -> Poll<io::Result<()>> {
        Pin::new(&mut self.tls).poll_read(cx, buf)
    }
}
impl AsyncWrite for Stream {
    fn poll_write(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &[u8],
    ) -> Poll<io::Result<usize>> {
        Pin::new(&mut self.tls).poll_write(cx, buf)
    }
    fn poll_flush(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut self.tls).poll_flush(cx)
    }
    fn poll_shutdown(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut self.tls).poll_shutdown(cx)
    }
}
impl Service<Uri> for Connector {
    type Response = TokioIo<Stream>;
    type Error = BoxError;
    type Future = Pin<Box<dyn Future<Output = Result<Self::Response, Self::Error>> + Send>>;
    fn poll_ready(&mut self, _: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
        Poll::Ready(Ok(()))
    }
    fn call(&mut self, uri: Uri) -> Self::Future {
        let this = self.clone();
        Box::pin(async move { this.connect(uri).await })
    }
}

type HttpClient = Client<Connector, Full<Bytes>>;
static CLIENTS: OnceLock<Mutex<Vec<(Settings, HttpClient)>>> = OnceLock::new();
fn client(settings: &Settings) -> Result<HttpClient, BoxError> {
    let mut clients = CLIENTS.get_or_init(Default::default).lock().unwrap();
    if let Some((_, client)) = clients.iter().find(|(s, _)| s == settings) {
        return Ok(client.clone());
    }
    let connector = Connector::new(settings)?;
    let client = Client::builder(TokioExecutor::new())
        .pool_idle_timeout(Duration::from_secs(20))
        .pool_max_idle_per_host(4)
        .retry_canceled_requests(false)
        .build(connector);
    if clients.len() >= 4 {
        clients.remove(0);
    }
    clients.push((settings.clone(), client.clone()));
    Ok(client)
}

async fn execute(input: Input) -> Result<Output, BoxError> {
    let response_limit = input.max_body.min(MAX_BODY);
    let uri: Uri = input
        .url
        .parse()
        .map_err(|_| fail("CONFIG", "invalid_url"))?;
    if uri.scheme_str() != Some("https") || uri.authority().is_none_or(|a| a.as_str().contains('@'))
    {
        return Err(fail("CONFIG", "https_required"));
    }
    let bytes = STANDARD
        .decode(input.body)
        .map_err(|_| fail("CONFIG", "invalid_body"))?;
    if bytes.len() > MAX_BODY {
        return Err(fail("CONFIG", "request_too_large"));
    }
    let mut request = Request::builder().method(input.method.as_str()).uri(uri);
    for (name, value) in input.headers {
        if [
            "host",
            "connection",
            "content-length",
            "transfer-encoding",
            "accept-encoding",
        ]
        .contains(&name.to_ascii_lowercase().as_str())
        {
            continue;
        }
        request = request.header(name, value);
    }
    let request = request
        .header(header::ACCEPT_ENCODING, "gzip")
        .body(Full::new(Bytes::from(bytes)))
        .map_err(|_| fail("CONFIG", "invalid_headers"))?;
    let response = client(&input.settings)?
        .request(request)
        .await
        .map_err(|e| {
            let mut cause: &(dyn std::error::Error + 'static) = &e;
            loop {
                if let Some(failure) = cause.downcast_ref::<Failure>() {
                    return fail(failure.stage, failure.code);
                }
                match cause.source() {
                    Some(next) => cause = next,
                    None => break,
                }
            }
            fail("HTTP", "connection_failed")
        })?;
    let code = response.status().as_u16();
    let mut facts = response
        .extensions()
        .get::<Facts>()
        .cloned()
        .unwrap_or(Facts {
            stage: "HTTP".into(),
            ech: "Unknown".into(),
            ip: None,
        });
    let gzip = response
        .headers()
        .get(header::CONTENT_ENCODING)
        .is_some_and(|v| v == "gzip");
    let headers = response
        .headers()
        .iter()
        .filter_map(|(name, value)| {
            value
                .to_str()
                .ok()
                .map(|v| (name.to_string(), v.to_string()))
        })
        .collect();
    let body = Limited::new(response.into_body(), response_limit)
        .collect()
        .await
        .map_err(|_| fail("HTTP_BODY", "body_read_or_size_limit"))?
        .to_bytes();
    let body = if gzip {
        let mut decoded = Vec::new();
        flate2::read::GzDecoder::new(body.as_ref())
            .take(response_limit as u64 + 1)
            .read_to_end(&mut decoded)
            .map_err(|_| fail("HTTP_BODY", "gzip_decode"))?;
        if decoded.len() > response_limit {
            return Err(fail("HTTP_BODY", "body_too_large"));
        }
        decoded
    } else {
        body.to_vec()
    };
    facts.stage = "COMPLETE".into();
    Ok(Output {
        code,
        headers,
        body: STANDARD.encode(body),
        error: None,
        facts,
    })
}

struct Job {
    abort: Option<AbortHandle>,
    result: Option<String>,
}
static RUNTIME: OnceLock<Runtime> = OnceLock::new();
static JOBS: OnceLock<Mutex<HashMap<u64, Job>>> = OnceLock::new();
static NEXT: AtomicU64 = AtomicU64::new(1);
fn jobs() -> &'static Mutex<HashMap<u64, Job>> {
    JOBS.get_or_init(Default::default)
}
fn runtime() -> &'static Runtime {
    RUNTIME.get_or_init(|| {
        let _ = rustls::crypto::aws_lc_rs::default_provider().install_default();
        tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .enable_all()
            .build()
            .expect("network runtime")
    })
}

/// # Safety
/// `request_json` must point to a valid NUL-terminated UTF-8 C string for this call only.
#[no_mangle]
pub unsafe extern "C" fn nodyssey_network_start(request_json: *const c_char) -> u64 {
    if request_json.is_null() {
        return 0;
    }
    let text = CStr::from_ptr(request_json).to_bytes();
    if text.len() > 48 * 1024 * 1024 {
        return 0;
    }
    let Ok(input) = serde_json::from_slice::<Input>(text) else {
        return 0;
    };
    let id = NEXT.fetch_add(1, Ordering::Relaxed);
    {
        let mut jobs = jobs().lock().unwrap();
        if jobs.len() >= 48 {
            return 0;
        }
        jobs.insert(
            id,
            Job {
                abort: None,
                result: None,
            },
        );
    }
    let handle = runtime().spawn(async move {
        let output = match tokio::time::timeout(TIMEOUT, execute(input)).await {
            Ok(Ok(output)) => output,
            error => {
                let (stage, code) = match error {
                    Ok(Err(ref e)) => e
                        .downcast_ref::<Failure>()
                        .map(|e| (e.stage, e.code))
                        .unwrap_or(("HTTP", "connection_failed")),
                    _ => ("TIMEOUT", "request_timeout"),
                };
                Output {
                    code: 0,
                    headers: Vec::new(),
                    body: String::new(),
                    error: Some(code.into()),
                    facts: Facts {
                        stage: stage.into(),
                        ech: match stage {
                            "HTTP" | "HTTP_BODY" | "TIMEOUT" => "Unknown",
                            _ => "NotEstablished",
                        }
                        .into(),
                        ip: None,
                    },
                }
            }
        };
        if let Some(job) = jobs().lock().unwrap().get_mut(&id) {
            job.result = serde_json::to_string(&output).ok();
        }
    });
    if let Some(job) = jobs().lock().unwrap().get_mut(&id) {
        job.abort = Some(handle.abort_handle());
    }
    id
}

#[no_mangle]
pub extern "C" fn nodyssey_network_take(id: u64) -> *mut c_char {
    let mut jobs = jobs().lock().unwrap();
    if jobs.get(&id).is_some_and(|job| job.result.is_some()) {
        let result = jobs.remove(&id).unwrap().result.unwrap();
        return CString::new(result).map_or(std::ptr::null_mut(), CString::into_raw);
    }
    std::ptr::null_mut()
}
#[no_mangle]
pub extern "C" fn nodyssey_network_cancel(id: u64) {
    if let Some(job) = jobs().lock().unwrap().remove(&id) {
        if let Some(abort) = job.abort {
            abort.abort();
        }
    }
}
/// # Safety
/// `result` must be NULL or an unfreed pointer returned by `nodyssey_network_take`.
#[no_mangle]
pub unsafe extern "C" fn nodyssey_network_free(result: *mut c_char) {
    if !result.is_null() {
        drop(CString::from_raw(result));
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn ech_configuration_cannot_silently_downgrade() {
        assert!(tls_config(Some(vec![0, 1, 0])).is_err());
        assert!(tls_config(None).is_ok());
    }
    #[tokio::test]
    async fn plaintext_and_url_credentials_are_rejected_before_network() {
        for url in ["http://example.com/", "https://name:password@example.com/"] {
            let input = Input {
                url: url.into(),
                method: "GET".into(),
                headers: vec![],
                body: String::new(),
                max_body: MAX_BODY,
                settings: Settings {
                    resolvers: vec![],
                    ipv6: false,
                },
            };
            assert_eq!(
                execute(input).await.unwrap_err().to_string(),
                "CONFIG:https_required"
            );
        }
    }
    #[test]
    fn cancel_removes_finished_results_and_is_idempotent() {
        let id = NEXT.fetch_add(1, Ordering::Relaxed);
        jobs().lock().unwrap().insert(
            id,
            Job {
                abort: None,
                result: Some("private body".into()),
            },
        );
        nodyssey_network_cancel(id);
        nodyssey_network_cancel(id);
        assert!(nodyssey_network_take(id).is_null());
    }
}
