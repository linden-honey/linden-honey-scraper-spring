package com.github.lindenhoney.scraper.service;

import com.github.lindenhoney.scraper.configuration.ApplicationProperties;
import com.github.lindenhoney.scraper.domain.Song;
import com.github.lindenhoney.scraper.domain.SongPreview;
import com.github.lindenhoney.scraper.util.GrobParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.retry.Retry;

import javax.validation.Validator;
import java.net.ConnectException;
import java.nio.charset.Charset;

@Slf4j
@Service
public class GrobScraper extends AbstractScraper {

    private static final String SOURCE_CHARSET = "windows-1251";

    public GrobScraper(ApplicationProperties properties, Validator validator) {
        super(properties, validator);
    }

    @Override
    public Flux<Song> fetchSongs() {
        log.debug("Songs fetching started");
        return fetchPreviews()
                .map(SongPreview::getId)
                .flatMapSequential(this::fetchSong)
                .doOnError(throwable -> log.error("Unexpected error happened during songs fetching", throwable))
                .doOnComplete(() -> log.debug("Songs fetching successfully finished"));
    }

    protected Flux<SongPreview> fetchPreviews() {
        return client.get()
                .uri("texts")
                .exchange()
                .flatMap(response -> response.bodyToMono(byte[].class))
                .map(bytes -> new String(bytes, Charset.forName(SOURCE_CHARSET)))
                .flatMapMany(html -> Flux.fromStream(GrobParser.parsePreviews(html)))
                .filter(this::validate);
    }

    protected Mono<Song> fetchSong(Long id) {
        log.trace("Fetching the song with id {}", id);
        return client
                .get()
                .uri(builder -> builder
                        .path("text_print.php")
                        .queryParam("area", "go_texts")
                        .queryParam("id", id)
                        .build())
                .exchange()
                .retryWhen(Retry
                        .anyOf(ConnectException.class)
                        .retryMax(properties.getRetry().getMaxRetries())
                        .exponentialBackoffWithJitter(properties.getRetry().getFirstBackoff(), properties.getRetry().getMaxBackoff())
                        .doOnRetry(context -> log.trace("Performing retry of fetching the song with id {} (attempt {}). Retry is caused by \"{}\"", id, context.iteration(), context.exception().getMessage())))
                .flatMap(response -> response.bodyToMono(byte[].class))
                .map(bytes -> new String(bytes, Charset.forName(SOURCE_CHARSET)))
                .flatMap(html -> Mono.justOrEmpty(GrobParser.parseSong(html)))
                .filter(this::validate)
                .doOnSuccess(song -> log.trace("Successfully fetched song with id {} and title \"{}\"", id, song.getTitle()));
    }
}