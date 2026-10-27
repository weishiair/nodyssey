//! Bounded RFC 1035/RFC 9460 wire decoding. Only the selected HTTPS resolver is queried.
use std::net::{IpAddr, Ipv4Addr, Ipv6Addr};

#[derive(Clone, Debug, Default)]
pub struct Answer {
    pub ips: Vec<IpAddr>,
    pub ech: Option<Vec<u8>>,
    pub ttl: u32,
    pub alias: Option<String>,
}

pub fn query(host: &str, kind: u16) -> Result<Vec<u8>, &'static str> {
    let mut out = vec![0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0];
    for label in host.trim_end_matches('.').split('.') {
        if label.is_empty() || label.len() > 63 || !label.is_ascii() {
            return Err("invalid_dns_name");
        }
        out.push(label.len() as u8);
        out.extend(label.as_bytes());
    }
    if out.len() > 266 {
        return Err("invalid_dns_name");
    }
    out.push(0);
    out.extend(kind.to_be_bytes());
    out.extend(1u16.to_be_bytes());
    Ok(out)
}

fn word(data: &[u8], pos: usize) -> Result<u16, &'static str> {
    let bytes = data.get(pos..pos + 2).ok_or("truncated_dns")?;
    Ok(u16::from_be_bytes([bytes[0], bytes[1]]))
}

fn name(data: &[u8], pos: &mut usize) -> Result<String, &'static str> {
    let mut cursor = *pos;
    let mut jumped = false;
    let mut labels = Vec::new();
    let mut size = 0;
    for _ in 0..128 {
        let len = *data.get(cursor).ok_or("truncated_dns")?;
        if len & 0xc0 == 0xc0 {
            let offset = (word(data, cursor)? & 0x3fff) as usize;
            if offset >= cursor {
                return Err("invalid_dns_pointer");
            }
            if !jumped {
                *pos = cursor + 2;
                jumped = true;
            }
            cursor = offset;
        } else if len == 0 {
            if !jumped {
                *pos = cursor + 1;
            }
            return Ok(labels.join(".").to_ascii_lowercase());
        } else {
            if len > 63 {
                return Err("invalid_dns_label");
            }
            cursor += 1;
            let label = data
                .get(cursor..cursor + len as usize)
                .ok_or("truncated_dns")?;
            if !label.is_ascii() {
                return Err("invalid_dns_label");
            }
            size += label.len() + 1;
            if size > 255 {
                return Err("invalid_dns_name");
            }
            labels.push(String::from_utf8_lossy(label).into_owned());
            cursor += len as usize;
        }
    }
    Err("dns_pointer_loop")
}

pub fn parse(data: &[u8], host: &str, kind: u16) -> Result<Answer, &'static str> {
    if data.len() < 12 || data.len() > 65535 || word(data, 0)? != 0 {
        return Err("invalid_dns_message");
    }
    let flags = word(data, 2)?;
    if flags & 0x8000 == 0 || flags & 0x0200 != 0 || flags & 0x780f != 0 {
        return Err("dns_response_error");
    }
    if word(data, 4)? != 1 || word(data, 6)? > 256 {
        return Err("invalid_dns_count");
    }
    let mut pos = 12;
    if name(data, &mut pos)? != host.trim_end_matches('.').to_ascii_lowercase()
        || word(data, pos)? != kind
        || word(data, pos + 2)? != 1
    {
        return Err("dns_question_mismatch");
    }
    pos += 4;
    let mut records = Vec::new();
    for _ in 0..word(data, 6)? {
        let owner = name(data, &mut pos)?;
        let record_type = word(data, pos)?;
        let class = word(data, pos + 2)?;
        let ttl = u32::from_be_bytes(
            data.get(pos + 4..pos + 8)
                .ok_or("truncated_dns")?
                .try_into()
                .unwrap(),
        );
        let len = word(data, pos + 8)? as usize;
        pos += 10;
        let start = pos;
        pos = pos.checked_add(len).ok_or("invalid_dns_length")?;
        if pos > data.len() {
            return Err("truncated_dns");
        }
        if class == 1 {
            records.push((owner, record_type, ttl, start, pos));
        }
    }
    let mut canonical = host.trim_end_matches('.').to_ascii_lowercase();
    let mut answer = Answer {
        ttl: 3600,
        ..Answer::default()
    };
    for _ in 0..8 {
        let cname = records.iter().find(|r| r.0 == canonical && r.1 == 5);
        if let Some(r) = cname {
            let mut p = r.3;
            canonical = name(data, &mut p)?;
            if p > r.4 {
                return Err("invalid_cname");
            }
            answer.ttl = answer.ttl.min(r.2);
            answer.alias = Some(canonical.clone());
        } else {
            break;
        }
    }
    let mut priority = u16::MAX;
    for (owner, record_type, ttl, start, end) in records {
        if owner != canonical || record_type != kind {
            continue;
        }
        answer.ttl = answer.ttl.min(ttl);
        match kind {
            1 if end - start == 4 => answer.ips.push(
                Ipv4Addr::new(
                    data[start],
                    data[start + 1],
                    data[start + 2],
                    data[start + 3],
                )
                .into(),
            ),
            28 if end - start == 16 => answer
                .ips
                .push(Ipv6Addr::from(<[u8; 16]>::try_from(&data[start..end]).unwrap()).into()),
            65 => {
                let order = word(data, start)?;
                let mut p = start + 2;
                let target = name(data, &mut p)?;
                if p > end {
                    return Err("invalid_https_record");
                }
                if order == 0 {
                    if !target.is_empty() {
                        answer.alias = Some(target);
                    }
                    continue;
                }
                while p < end {
                    let key = word(data, p)?;
                    let len = word(data, p + 2)? as usize;
                    p += 4;
                    if p + len > end {
                        return Err("invalid_https_parameter");
                    }
                    if key == 5 && len > 0 && order <= priority {
                        answer.ech = Some(data[p..p + len].to_vec());
                        priority = order;
                    }
                    p += len;
                }
            }
            _ => {}
        }
    }
    Ok(answer)
}

#[cfg(test)]
mod tests {
    use super::*;
    fn response(kind: u16, rdata: &[u8]) -> Vec<u8> {
        let mut packet = query("example.com", kind).unwrap();
        packet[2] = 0x81;
        packet[3] = 0x80;
        packet[7] = 1;
        packet.extend([0xc0, 0x0c]);
        packet.extend(kind.to_be_bytes());
        packet.extend([0, 1, 0, 0, 0, 60]);
        packet.extend((rdata.len() as u16).to_be_bytes());
        packet.extend(rdata);
        packet
    }
    #[test]
    fn compressed_owner_and_ipv4() {
        assert_eq!(
            parse(&response(1, &[1, 2, 3, 4]), "example.com", 1)
                .unwrap()
                .ips,
            vec![IpAddr::from([1, 2, 3, 4])]
        );
    }
    #[test]
    fn https_ech_preserves_binary_list() {
        let packet = response(65, &[0, 1, 0, 0, 5, 0, 4, 0, 2, 254, 13]);
        assert_eq!(
            parse(&packet, "example.com", 65).unwrap().ech,
            Some(vec![0, 2, 254, 13])
        );
    }
    #[test]
    fn malformed_parameter_is_rejected() {
        assert!(parse(&response(65, &[0, 1, 0, 0, 5, 0, 8, 1]), "example.com", 65).is_err());
    }
    #[test]
    fn wrong_question_and_truncation_are_rejected() {
        let packet = response(1, &[1, 2, 3, 4]);
        assert!(parse(&packet, "attacker.example", 1).is_err());
        for end in 0..packet.len() {
            assert!(parse(&packet[..end], "example.com", 1).is_err());
        }
    }
    #[test]
    fn pointer_cycle_is_rejected() {
        let mut pos = 0;
        assert!(name(&[0xc0, 0], &mut pos).is_err());
    }
}
