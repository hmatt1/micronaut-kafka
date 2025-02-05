/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.kafka.streams

import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.core.util.CollectionUtils
import io.micronaut.inject.qualifiers.Qualifiers
import org.apache.kafka.streams.KafkaStreams
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class KafkaStreamsSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            CollectionUtils.mapOf(
                    "kafka.bootstrap.servers", 'localhost:${random.port}',
                    AbstractKafkaConfiguration.EMBEDDED, true,
                    AbstractKafkaConfiguration.EMBEDDED_TOPICS, [
                        WordCountStream.INPUT,
                        WordCountStream.OUTPUT,
                        WordCountStream.NAMED_WORD_COUNT_INPUT,
                        WordCountStream.NAMED_WORD_COUNT_OUTPUT
                    ],
                    'kafka.generic.config', "hello",
                    'kafka.streams.my-stream.application.id','my-stream',
                    'kafka.streams.my-stream.num.stream.threads', 10
            )
    )

    void "test config"() {
        when:
        def builder = context.getBean(ConfiguredStreamBuilder, Qualifiers.byName('my-stream'))

        then:
        builder.configuration['application.id'] == "my-stream"
        builder.configuration['generic.config'] == "hello"
    }

    void "test config from stream"() {
        when:
        def stream = context.getBean(KafkaStreams, Qualifiers.byName('my-stream'))

        then:
        stream.config.originals().get('application.id') == "my-stream"
        stream.config.originals().get('generic.config') == "hello"
    }

    void "test kafka stream application"() {
        given:
        InteractiveQueryServiceExample interactiveQueryService = context.getBean(InteractiveQueryServiceExample)
        PollingConditions conditions = new PollingConditions(timeout: 40, delay: 1)

        when:
        WordCountClient wordCountClient = context.getBean(WordCountClient)
        wordCountClient.publishSentence("The quick brown fox jumps over the lazy dog. THE QUICK BROWN FOX JUMPED OVER THE LAZY DOG'S BACK")

        WordCountListener countListener = context.getBean(WordCountListener)

        then:
        conditions.eventually {
            countListener.getCount("fox") > 0
            countListener.getCount("jumps") > 0
            interactiveQueryService.getWordCount(WordCountStream.WORD_COUNT_STORE, "fox") > 0
            interactiveQueryService.getWordCount(WordCountStream.WORD_COUNT_STORE, "jumps") > 0
            interactiveQueryService.<String, Long>getGenericKeyValue(WordCountStream.WORD_COUNT_STORE, "the") > 0

            println countListener.wordCounts
            println interactiveQueryService.getWordCount(WordCountStream.WORD_COUNT_STORE, "fox")
            println interactiveQueryService.getWordCount(WordCountStream.WORD_COUNT_STORE, "jumps")
            println interactiveQueryService.<String, Long>getGenericKeyValue(WordCountStream.WORD_COUNT_STORE, "the")
        }

    }
}
