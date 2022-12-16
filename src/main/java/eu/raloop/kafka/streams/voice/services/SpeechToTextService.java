package eu.raloop.kafka.streams.voice.services;

import eu.raloop.kafka.streams.voice.model.ParsedVoiceCommand;
import eu.raloop.kafka.streams.voice.model.VoiceCommand;

public interface SpeechToTextService {

    ParsedVoiceCommand speechToText(VoiceCommand voiceCommand);
}
