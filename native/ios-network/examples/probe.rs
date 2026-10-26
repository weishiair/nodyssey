//! Read-only live probe. Prints connection facts and status, never response headers or body.
use nodyssey_network::{
    nodyssey_network_cancel, nodyssey_network_free, nodyssey_network_start, nodyssey_network_take,
};
use std::{
    ffi::{CStr, CString},
    time::{Duration, Instant},
};

fn main() {
    let input = CString::new(r#"{"url":"https://www.nodeseek.com/","method":"GET","headers":[],"body":"","max_body":1048576,"settings":{"resolvers":[{"url":"https://resolver.weishiair.de/dns-query","bootstrap":[]}],"ipv6":false}}"#).unwrap();
    let id = unsafe { nodyssey_network_start(input.as_ptr()) };
    assert_ne!(id, 0);
    let started = Instant::now();
    loop {
        let ptr = nodyssey_network_take(id);
        if !ptr.is_null() {
            let text = unsafe { CStr::from_ptr(ptr).to_string_lossy().into_owned() };
            unsafe {
                nodyssey_network_free(ptr);
            }
            let value: serde_json::Value = serde_json::from_str(&text).unwrap();
            println!(
                "status={} facts={} error={}",
                value["code"], value["facts"], value["error"]
            );
            assert!(value["error"].is_null(), "live connection failed");
            assert_eq!(
                value["facts"]["ech"], "Accepted",
                "production ECH must be negotiated"
            );
            break;
        }
        if started.elapsed() > Duration::from_secs(40) {
            nodyssey_network_cancel(id);
            panic!("native request did not finish");
        }
        std::thread::sleep(Duration::from_millis(25));
    }
}
