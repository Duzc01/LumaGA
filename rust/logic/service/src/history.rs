use crate::error::ServiceResult;
use cache::CACHE;
use chrono::Utc;
use protos::{
    DataModel::{Topic, TopicSnapshot},
    Message,
    Service::{
        DeleteTopicHistoryRequest, DeleteTopicHistoryResponse, TopicHistoryRequest,
        TopicHistoryResponse, UpdateTopicProgressRequest,
    },
};
use std::cmp::Reverse;

pub static TOPIC_SNAPSHOT_PREFIX: &str = "/snapshot/topic";
fn topic_snapshot_key(id: &str) -> String {
    format!("{}/{}", TOPIC_SNAPSHOT_PREFIX, id)
}

pub fn insert_topic_history(topic: Topic) {
    let key = topic_snapshot_key(topic.get_id());
    let snapshot = TopicSnapshot {
        topic_snapshot: Some(topic).into(),
        timestamp: Utc::now().timestamp_millis() as u64,
        ..Default::default()
    };
    let _ = CACHE.insert_msg(&key, &snapshot);
}

pub fn find_topic_history(topic_id: &str) -> Option<TopicSnapshot> {
    let key = topic_snapshot_key(topic_id);
    CACHE.get_msg::<TopicSnapshot>(&key).ok().flatten()
}

pub fn update_topic_progress(request: UpdateTopicProgressRequest) {
    let UpdateTopicProgressRequest {
        topic_id,
        highest_floor,
        current_floor,
        ..
    } = request;
    let key = topic_snapshot_key(&topic_id);
    let _ = CACHE.mutate_msg(&key, |snapshot: &mut TopicSnapshot| {
        let topic = snapshot.mut_topic_snapshot();
        if highest_floor > topic.get_highest_viewed_floor() {
            topic.set_highest_viewed_floor(highest_floor);
        }
        topic.set_last_viewing_floor(current_floor);
    });
}

pub async fn get_topic_history(
    request: TopicHistoryRequest,
) -> ServiceResult<TopicHistoryResponse> {
    let snapshots = {
        let mut ss = tokio::task::block_in_place(|| {
            CACHE
                .scan_prefix(TOPIC_SNAPSHOT_PREFIX)
                .filter_map(|p| p.ok())
                .filter_map(|(_k, v)| TopicSnapshot::parse_from_bytes(&v).ok())
                .collect::<Vec<_>>()
        });

        ss.sort_by_key(|s| Reverse(s.timestamp)); // todo: use heap
        let _ = ss.split_off((request.limit as usize).min(ss.len()));
        ss
    };

    Ok(TopicHistoryResponse {
        topics: snapshots.into(),
        ..Default::default()
    })
}

pub async fn delete_topic_history(
    request: DeleteTopicHistoryRequest,
) -> ServiceResult<DeleteTopicHistoryResponse> {
    let key = topic_snapshot_key(request.get_topic_id());
    let deleted = CACHE.remove_key(&key).unwrap_or(false);
    Ok(DeleteTopicHistoryResponse {
        deleted,
        ..Default::default()
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::utils::get_unique_id;

    #[tokio::test]
    async fn test_delete_topic_history() {
        let topic_id = format!("test-delete-{}", get_unique_id());
        insert_topic_history(Topic {
            id: topic_id.clone(),
            ..Default::default()
        });
        assert!(find_topic_history(&topic_id).is_some());

        let request = DeleteTopicHistoryRequest {
            topic_id: topic_id.clone(),
            ..Default::default()
        };
        let response = delete_topic_history(request).await.unwrap();
        assert!(response.deleted);
        assert!(find_topic_history(&topic_id).is_none());

        // Deleting a missing entry reports deleted=false and is harmless.
        let request = DeleteTopicHistoryRequest {
            topic_id,
            ..Default::default()
        };
        let response = delete_topic_history(request).await.unwrap();
        assert!(!response.deleted);
    }
}
