package eu.raloop.kafka.streams.voice;

import eu.raloop.kafka.streams.voice.config.StreamsConfiguration;
import eu.raloop.kafka.streams.voice.services.MockSttClient;
import eu.raloop.kafka.streams.voice.services.MockTranslateClient;
import org.apache.kafka.streams.KafkaStreams;

public class VoiceCommandParserApp {
    public static void main(String[] args) {
        var voiceCommandParserTopology = new VoiceCommandParserTopology(new MockSttClient(), new MockTranslateClient(), 0.90);
        var streamsConfiguration = new StreamsConfiguration();
        var kafkaStreams = new KafkaStreams(voiceCommandParserTopology.createTopology(), streamsConfiguration.createConfig());

        kafkaStreams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));
    }
}
