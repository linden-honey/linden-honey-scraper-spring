package com.github.lindenhoney.scraper.controller;

import com.github.lindenhoney.scraper.domain.Song;
import com.github.lindenhoney.scraper.service.Scraper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.http.MediaType.APPLICATION_STREAM_JSON_VALUE;

@ConditionalOnBean(Scraper.class)
@RestController
@RequiredArgsConstructor
public class ScraperController {

    private final Scraper scraper;

    @GetMapping(
            value = "/songs",
            produces = {
                    APPLICATION_JSON_UTF8_VALUE,
                    APPLICATION_STREAM_JSON_VALUE
            }
    )
    public Flux<Song> getSongs() {
        return scraper.fetchSongs();
    }
}
