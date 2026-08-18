package com.bugenzhao.mnga.ui.editor

import com.bugenzhao.mnga.model.GenericPostModel
import com.bugenzhao.mnga.model.PlusFeature
import com.bugenzhao.mnga.model.PlusModel
import com.bugenzhao.mnga.protos.datamodel.Forum
import com.bugenzhao.mnga.protos.datamodel.ForumId
import com.bugenzhao.mnga.protos.datamodel.Post
import com.bugenzhao.mnga.protos.datamodel.PostId
import com.bugenzhao.mnga.protos.datamodel.PostReplyAction
import com.bugenzhao.mnga.protos.datamodel.Topic
import kotlinx.coroutines.CoroutineScope

/**
 * Facade over the two editor models, mirroring the SwiftUI environment
 * objects (`postReply` / `postModel`) each screen used to own. Create one per
 * hosting screen and render `PostEditorSheet` / `ShortMessageEditorSheet`
 * while the corresponding `showEditor` flow is true.
 */
class EditorController(val scope: CoroutineScope) {

    val postReply = PostReplyModel(scope)
    val shortMessage = ShortMessageModel(scope)

    /** Reply to a post, mirroring `PostRowView.doReply`. */
    fun reply(post: Post) {
        postReply.show(replyAction(post, PostReplyAction.Operation.REPLY))
    }

    /** Quote a post, mirroring `PostRowView.doQuote`. */
    fun quote(post: Post) {
        postReply.show(replyAction(post, PostReplyAction.Operation.QUOTE))
    }

    /** Attach a comment to a post, mirroring `PostRowView.doComment`. */
    fun comment(post: Post) {
        // A comment lands on its host post's page, not on the topic's last one.
        postReply.show(
            replyAction(
                post,
                PostReplyAction.Operation.COMMENT,
                GenericPostModel.PageToReload.Exact(post.atPage),
            ),
        )
    }

    /** Edit (or append to) one's own post, mirroring `PostRowView.doEdit`. */
    fun modify(post: Post) {
        postReply.show(
            replyAction(
                post,
                PostReplyAction.Operation.MODIFY,
                GenericPostModel.PageToReload.Exact(post.atPage),
            ),
        )
    }

    /** Report a post, mirroring `PostRowView.doReport` (no forum id). */
    fun report(post: Post) {
        postReply.show(
            PostReplyTask(
                action = PostReplyAction.newBuilder()
                    .setOperation(PostReplyAction.Operation.REPORT)
                    .setPostId(post.id)
                    .build(),
            ),
        )
    }

    /** Create a new topic in a forum, Plus-gated like `TopicListView.newTopic`. */
    fun newTopic(forum: Forum) {
        if (!PlusModel.checkPlus(PlusFeature.NEW_TOPIC)) return
        postReply.show(
            PostReplyTask(
                action = PostReplyAction.newBuilder()
                    .setOperation(PostReplyAction.Operation.NEW)
                    .setForumId(forum.id)
                    .build(),
            ),
        )
    }

    /** Reply to the topic itself, mirroring `TopicDetailsView.doReplyTopic`. */
    fun replyTopic(topic: Topic) {
        postReply.show(
            PostReplyTask(
                action = PostReplyAction.newBuilder()
                    .setOperation(PostReplyAction.Operation.REPLY)
                    .setForumId(ForumId.newBuilder().setFid(topic.fid).build())
                    .setPostId(
                        PostId.newBuilder().setTid(topic.id).setPid("0").build(),
                    )
                    .build(),
            ),
        )
    }

    private fun replyAction(
        post: Post,
        operation: PostReplyAction.Operation,
        pageToReload: GenericPostModel.PageToReload = GenericPostModel.PageToReload.Last,
    ) =
        PostReplyTask(
            action = PostReplyAction.newBuilder()
                .setOperation(operation)
                .setPostId(post.id)
                .setForumId(ForumId.newBuilder().setFid(post.fid).build())
                .build(),
            pageToReload = pageToReload,
        )
}
