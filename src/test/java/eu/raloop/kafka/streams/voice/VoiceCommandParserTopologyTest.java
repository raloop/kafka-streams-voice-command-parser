package eu.raloop.kafka.streams.voice;


import eu.raloop.kafka.streams.voice.model.ParsedVoiceCommand;
import eu.raloop.kafka.streams.voice.model.VoiceCommand;
import eu.raloop.kafka.streams.voice.serdes.JsonSerde;
import eu.raloop.kafka.streams.voice.services.SpeechToTextService;
import eu.raloop.kafka.streams.voice.services.TranslateService;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Properties;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VoiceCommandParserTopologyTest {

    @Mock
    private SpeechToTextService speechToTextService;
    @Mock
    private TranslateService translateService;

    private VoiceCommandParserTopology voiceCommandParserTopology;

    private TopologyTestDriver topologyTestDriver;
    private TestInputTopic<String, VoiceCommand> voiceCommandInputTopic;
    private TestOutputTopic<String, ParsedVoiceCommand> recognizedCommandsOutputTopic;
    private TestOutputTopic<String, ParsedVoiceCommand> unrecognizedVoiceCommandsOutputTopic;

    @BeforeEach
    void setUp() {
        voiceCommandParserTopology = new VoiceCommandParserTopology(speechToTextService, translateService, 0.90);
        var topology = voiceCommandParserTopology.createTopology();
        var props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy-1234");
        topologyTestDriver = new TopologyTestDriver(topology, props);
        var voiceCommandJsonSerde = new JsonSerde<VoiceCommand>(VoiceCommand.class);
        var parsedVoiceCommandJsonSerde = new JsonSerde<ParsedVoiceCommand>(ParsedVoiceCommand.class);
        voiceCommandInputTopic = topologyTestDriver.createInputTopic(VoiceCommandParserTopology.VOICE_COMMANDS_TOPIC,
                Serdes.String().serializer(), voiceCommandJsonSerde.serializer());
        recognizedCommandsOutputTopic = topologyTestDriver.createOutputTopic(VoiceCommandParserTopology.RECOGNIZED_COMMANDS_TOPIC,
                Serdes.String().deserializer(), parsedVoiceCommandJsonSerde.deserializer());
        unrecognizedVoiceCommandsOutputTopic = topologyTestDriver.createOutputTopic(VoiceCommandParserTopology.UNRECOGNIZED_COMMANDS_TOPIC,
                Serdes.String().deserializer(), parsedVoiceCommandJsonSerde.deserializer());

    }

    @Test
    @DisplayName("Given an English voice command, When processed correctly Then I receive a ParsedVoiceCommand in the recognized-commands topic")
    void testScenario1() {
        // Preconditions (Given)
        byte[] randomBytes = new byte[20];
        new Random().nextBytes(randomBytes);
        var voiceCommand = VoiceCommand.builder()
                .id(UUID.randomUUID().toString())
                .audio(randomBytes)
                .audioCodec("FLAC")
                .language("en-US")
                .build();
        var parsedVoiceCommand1 = ParsedVoiceCommand.builder()
                .id(voiceCommand.getId())
                .language("en-US")
                .text("call john")
                .probability(0.98)
                .build();
        given(speechToTextService.speechToText(voiceCommand)).willReturn(parsedVoiceCommand1);
        // Actions (When)
        voiceCommandInputTopic.pipeInput(voiceCommand);
        // Verifications (Then)
        var parsedVoiceCommand = recognizedCommandsOutputTopic.readRecord().value();

        assertEquals(voiceCommand.getId(), parsedVoiceCommand.getId());
        assertEquals("call john", parsedVoiceCommand.getText());
    }

    @Test
    @DisplayName("Given a non-English voice command, When processed correctly Then I receive a ParsedVoiceCommand in the recognized-commands topic")
    void testScenario2() {
        // Preconditions (Given)
        byte[] randomBytes = new byte[20];
        new Random().nextBytes(randomBytes);
        var voiceCommand = VoiceCommand.builder()
                .id(UUID.randomUUID().toString())
                .audio(randomBytes)
                .audioCodec("FLAC")
                .language("es-AR")
                .build();
        var parsedVoiceCommand1 = ParsedVoiceCommand.builder()
                .id(voiceCommand.getId())
                .text("llamar a Juan")
                .language("es-AR")
                .probability(0.98)
                .build();
        given(speechToTextService.speechToText(voiceCommand)).willReturn(parsedVoiceCommand1);
        var translatedVoiceCommand = ParsedVoiceCommand.builder()
                .id(voiceCommand.getId())
                .text("call juan")
                .language("en-US")
                .build();
        given(translateService.translate(parsedVoiceCommand1)).willReturn(translatedVoiceCommand);
        // Actions (When)
        voiceCommandInputTopic.pipeInput(voiceCommand);
        // Verifications (Then)
        var parsedVoiceCommand = recognizedCommandsOutputTopic.readRecord().value();

        assertEquals(voiceCommand.getId(), parsedVoiceCommand.getId());
        assertEquals("call juan", parsedVoiceCommand.getText());
    }

    @Test
    @DisplayName("Given a non-recognizable voice command, When processed correctly Then I receive a ParsedVoiceCommand in the unrecognized-commands topic")
    void testScenario3() {
        // Preconditions (Given)
        byte[] randomBytes = new byte[20];
        new Random().nextBytes(randomBytes);
        var voiceCommand = VoiceCommand.builder()
                .id(UUID.randomUUID().toString())
                .audio(randomBytes)
                .audioCodec("FLAC")
                .language("en-US")
                .build();
        var parsedVoiceCommand1 = ParsedVoiceCommand.builder()
                .id(voiceCommand.getId())
                .language("en-US")
                .text("call john")
                .probability(0.75)
                .build();
        given(speechToTextService.speechToText(voiceCommand)).willReturn(parsedVoiceCommand1);
        // Actions (When)
        voiceCommandInputTopic.pipeInput(voiceCommand);
        // Verifications (Then)
        var parsedVoiceCommand = unrecognizedVoiceCommandsOutputTopic.readRecord().value();

        assertEquals(voiceCommand.getId(), parsedVoiceCommand.getId());
        assertTrue(recognizedCommandsOutputTopic.isEmpty());

        verify(translateService, never()).translate(any(ParsedVoiceCommand.class));
    }

    @Test
    @DisplayName("Given voice command that is too short (less than 10 bytes), When processed correctly Then I don’t receive any command in any of the output topics")
    void testScenario4() {
        // Preconditions (Given)
        byte[] randomBytes = new byte[9];
        new Random().nextBytes(randomBytes);
        var voiceCommand = VoiceCommand.builder()
                .id(UUID.randomUUID().toString())
                .audio(randomBytes)
                .audioCodec("FLAC")
                .language("en-US")
                .build();

        // Actions (When)
        voiceCommandInputTopic.pipeInput(voiceCommand);
        // Verifications (Then)
        assertTrue(recognizedCommandsOutputTopic.isEmpty());
        assertTrue(unrecognizedVoiceCommandsOutputTopic.isEmpty());

        verify(speechToTextService, never()).speechToText(any(VoiceCommand.class));
        verify(translateService, never()).translate(any(ParsedVoiceCommand.class));
    }
}