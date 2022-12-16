package eu.raloop.kafka.streams.voice.services;

import eu.raloop.kafka.streams.voice.model.ParsedVoiceCommand;

public interface TranslateService {

    ParsedVoiceCommand translate(ParsedVoiceCommand original);
}
