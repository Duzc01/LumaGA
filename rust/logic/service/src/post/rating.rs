use protos::DataModel::{PostContent, Span, Span_Plain};
use std::collections::HashMap;

fn rating_summary(vote: &str) -> Option<(String, String)> {
    let parts: Vec<_> = vote.split('~').collect();
    let fields: HashMap<_, _> = parts
        .chunks_exact(2)
        .map(|pair| (pair[0], pair[1]))
        .collect();
    if fields.get("type") != Some(&"2") {
        return None;
    }

    let mut sum_hundredths = 0;
    let mut items = 0;
    let mut people = 0;
    for key in fields
        .keys()
        .filter(|key| key.parse::<u32>().is_ok_and(|id| id != 0))
    {
        let mut totals = fields.get(format!("_{key}").as_str())?.split(',');
        let votes: u64 = totals.next()?.parse::<u32>().ok()?.into();
        let points: u64 = totals.next()?.parse::<u32>().ok()?.into();
        people = people.max(
            totals
                .next()
                .and_then(|n| n.parse::<u32>().ok())
                .unwrap_or(0),
        );
        sum_hundredths += (points * 100).checked_div(votes).unwrap_or(0);
        items += 1;
    }
    if items == 0 || people == 0 {
        return None;
    }

    // NGA truncates each item's average to hundredths and the card average to tenths.
    let tenths = sum_hundredths / items / 10;
    let score = if tenths % 10 == 0 {
        (tenths / 10).to_string()
    } else {
        format!("{}.{}", tenths / 10, tenths % 10)
    };
    Some((score, people.to_string()))
}

pub(super) fn fill_placeholders(content: &mut PostContent, vote: &str) {
    fn fill(spans: &mut [Span], score: &str, people: &str) {
        for span in spans.iter_mut().filter(|span| span.has_tagged()) {
            let tag = span.mut_tagged();
            let value = if tag.tag == "style" {
                tag.complex_attributes.windows(2).find_map(|pair| {
                    if !pair[0].eq_ignore_ascii_case("innerhtml") {
                        return None;
                    }
                    match pair[1].as_str() {
                        "$votedata_voteavgvalue" => Some(score),
                        "$votedata_usernum" => Some(people),
                        _ => None,
                    }
                })
            } else {
                None
            };
            if let Some(value) = value {
                let mut text = Span::new();
                text.set_plain(Span_Plain {
                    text: value.to_owned(),
                    ..Default::default()
                });
                tag.spans = vec![text].into();
            } else {
                fill(&mut tag.spans, score, people);
            }
        }
    }

    if content.raw.contains("$votedata_")
        && let Some((score, people)) = rating_summary(vote)
    {
        fill(&mut content.spans, &score, &people);
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn fills_score_and_count_from_nga_vote_without_changing_raw_post() {
        let mut content = text::parse_content(concat!(
            "[style font 4 line-height 1.3 innerHTML &#36;votedata_voteavgvalue][/style]",
            "[style font 1 line-height 1.2][style innerHTML &#36;votedata_usernum][/style]人评分[/style]",
        ));
        let raw = content.raw.clone();
        fill_placeholders(
            &mut content,
            "52957~评分~type~2~min~1~max~10~_52957~75,667,75",
        );
        assert_eq!(content.raw, raw);
        let score = content.spans[0].get_tagged();
        let count = content.spans[1].get_tagged().spans[0].get_tagged();
        assert_eq!(score.spans[0].get_plain().text, "8.8");
        assert_eq!(count.spans[0].get_plain().text, "75");
    }

    #[test]
    fn skips_other_vote_types_and_missing_totals() {
        assert_eq!(rating_summary("52957~10~type~3"), None);
        assert_eq!(rating_summary("52957~评分~type~2~_52957~0,0,0"), None);
        assert_eq!(rating_summary("52957~评分~type~2~_52957~75,667"), None);
    }
}
