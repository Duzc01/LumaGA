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

fn clock_in_stats_key() -> String {
    format!("/clock_in_stats/user/{}", current_uid())
}

fn clocked_in_today() -> ServiceResult<bool> {
    let last = CACHE.get_msg::<ClockInResponse>(&clock_in_key())?;
    Ok(last
        .map(|r| r.date == server_today_string())
        .unwrap_or_default())
}

/// 签到统计（签到墙口径：连续/累计天数、金币/N币），当日缓存一次。
async fn fetch_stats() -> ServiceResult<ClockInResponse> {
    if let Some(cached) = CACHE.get_msg::<ClockInResponse>(&clock_in_stats_key())? {
        if cached.date == server_today_string() {
            return Ok(cached);
        }
    }
    let v = fetch_json_value(
        "nuke.php",
        vec![
            ("__lib", "check_in"),
            ("__act", "get_stat"),
            ("__output", "11"),
            ("app_id", "1010"),
            ("device", "android;AjBRHSD"),
        ],
        vec![],
    )
    .await?;
    let mut resp = ClockInResponse {
        date: server_today_string(),
        ..Default::default()
    };
    resp.continued_days = v["0"]["continued"].as_i64().unwrap_or(0) as i32;
    resp.total_days = v["0"]["sum"].as_i64().unwrap_or(0) as i32;
    resp.money = v["1"]["money"].as_i64().unwrap_or(0) as i32;
    resp.money_n = v["1"]["money_n"].as_i64().unwrap_or(0) as i32;
    let _ = CACHE.insert_msg(&clock_in_stats_key(), &resp)?;
    Ok(resp)
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

    // 合并签到统计（当日缓存，失败不影响签到主流程）。
    if let Ok(stats) = fetch_stats().await {
        response.continued_days = stats.continued_days;
        response.total_days = stats.total_days;
        response.money = stats.money;
        response.money_n = stats.money_n;
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
