use cache::CACHE;
use protos::Service::{ClockInRequest, ClockInResponse};

use crate::{
    auth::current_uid,
    error::{ServiceError, ServiceResult},
    fetch::fetch_json_value,
    utils::server_today_string,
};

fn clock_in_key() -> String {
    format!("/clock_in/user/{}", current_uid())
}

fn clocked_in_today() -> ServiceResult<bool> {
    let last = CACHE.get_msg::<ClockInResponse>(&clock_in_key())?;
    Ok(last
        .map(|r| r.date == server_today_string())
        .unwrap_or_default())
}

pub async fn clock_in(_request: ClockInRequest) -> ServiceResult<ClockInResponse> {
    let mut response = ClockInResponse {
        date: server_today_string(),
        ..Default::default()
    };

    if !clocked_in_today()? {
        match fetch_json_value(
            "nuke.php",
            vec![("__lib", "check_in"), ("__act", "check_in")],
            vec![],
        )
        .await
        {
            Ok(_) => response.is_first_time = true,
            // 服务器提示"今天已经签过"（本地缓存过期 / 其他端已签）：视为
            // 已签，记录缓存并返回成功，避免每次点击都重复请求。
            Err(ServiceError::Nga(e))
                if e.info.contains("已经签到") || e.info.contains("已签到") => {}
            Err(e) => return Err(e),
        }
        let _ = CACHE.insert_msg(&clock_in_key(), &response)?;
    }

    Ok(response)
}

#[cfg(test)]
mod test {
    use super::*;

    #[ignore = "manual: requires network or mutable external state"]
    #[tokio::test]
    async fn test_clock_in() -> ServiceResult<()> {
        clock_in(ClockInRequest::default()).await?;
        assert!(clocked_in_today().unwrap());
        Ok(())
    }
}
