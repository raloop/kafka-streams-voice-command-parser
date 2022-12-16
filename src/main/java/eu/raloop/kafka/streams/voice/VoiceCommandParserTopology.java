package eu.raloop.kafka.streams.voice;

import eu.raloop.kafka.streams.voice.model.ParsedVoiceCommand;
import eu.raloop.kafka.streams.voice.model.VoiceCommand;
import eu.raloop.kafka.streams.voice.serdes.JsonSerde;
import eu.raloop.kafka.streams.voice.services.SpeechToTextService;
import eu.raloop.kafka.streams.voice.services.TranslateService;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;

public class VoiceCommandParserTopology {

    public static final String VOICE_COMMANDS_TOPIC = "voice-commands";
    public static final String RECOGNIZED_COMMANDS_TOPIC = "recognized-commands";
    public static String UNRECOGNIZED_COMMANDS_TOPIC = "unrecognized-commands";
    private final SpeechToTextService speechToTextService;
    private final TranslateService translateService;
    private final Double certaintyThreshold;

    public VoiceCommandParserTopology(SpeechToTextService speechToTextService, TranslateService translateService, Double certaintyThreshold) {
        this.speechToTextService = speechToTextService;
        this.translateService = translateService;
        this.certaintyThreshold = certaintyThreshold;
    }

    public Topology createTopology() {
        var streamsBuilder = new StreamsBuilder();

        var voiceCommandJsonSerde = new JsonSerde<>(VoiceCommand.class);
        var parsedVoiceCommandJsonSerde = new JsonSerde<ParsedVoiceCommand>(ParsedVoiceCommand.class);
        var branches = streamsBuilder.stream(VOICE_COMMANDS_TOPIC, Consumed.with(Serdes.String(), voiceCommandJsonSerde))
                .filter((key, value) -> value.getAudio().length >= 10)
                .mapValues((readOnlyKey, value) -> speechToTextService.speechToText(value))
                .split(Named.as("branches-"))
                .branch((key, value) -> value.getProbability() > certaintyThreshold, Branched.as("recognized"))
                .defaultBranch(Branched.as("unrecognized"));
        branches.get("branches-unrecognized")
                .to(UNRECOGNIZED_COMMANDS_TOPIC, Produced.with(Serdes.String(), parsedVoiceCommandJsonSerde));

        var streamsMap = branches.get("branches-recognized")
                .split(Named.as("language-"))
                .branch((key, value) -> value.getLanguage().startsWith("en"), Branched.as("english"))
                .defaultBranch(Branched.as("non-english"));
        streamsMap.get("language-non-english")
                .mapValues((readOnlyKey, value) -> translateService.translate(value))
                .merge(streamsMap.get("language-english"))
                .to(RECOGNIZED_COMMANDS_TOPIC, Produced.with(Serdes.String(), parsedVoiceCommandJsonSerde));

        return streamsBuilder.build();
    }
}
