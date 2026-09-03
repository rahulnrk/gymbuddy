package com.gymbuddy.domain.voice.fake

import com.gymbuddy.domain.voice.TopicKind
import com.gymbuddy.domain.voice.TopicScope

class FakeTopicScope(
    var next: TopicKind = TopicKind.IN_SCOPE,
) : TopicScope {
    override fun classify(transcript: String): TopicKind = next
}