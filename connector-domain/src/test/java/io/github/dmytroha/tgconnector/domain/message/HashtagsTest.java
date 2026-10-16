package io.github.dmytroha.tgconnector.domain.message;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HashtagsTest {

    @Test
    void extractsDistinctLowerCaseHashtagsInOrder() {
        var text = "РАБОТА В ПРАГЕ\n\n#Склад #логистика, #склад\nпишите: @manager #Їжа_та_кава!";

        assertThat(Hashtags.extract(text)).containsExactly("склад", "логистика", "їжа_та_кава");
    }

    @Test
    void ignoresHashInsideWordsAndBareHashes() {
        assertThat(Hashtags.extract("mail me: a#b, issue #, C# and ##double")).containsExactly("double");
        assertThat(Hashtags.extract(null)).isEmpty();
    }

    @Test
    void normalizesUserInput() {
        assertThat(Hashtags.normalize(List.of("#Реклама", " склад ", "", "##x"))).containsExactlyInAnyOrder("реклама", "склад", "x");
    }

    @Test
    void criteriaFilterByHashtags() {
        var clock = java.time.Clock.systemUTC();
        var source = io.github.dmytroha.tgconnector.domain.source.Source.register(
                io.github.dmytroha.tgconnector.domain.source.ChatReference.parse("@jobs"), null, clock);
        var ad = source.accept(new io.github.dmytroha.tgconnector.domain.source.TelegramMessageId(1),
                MessageContent.text("Скидка! #реклама"), null, clock.instant(), clock).orElseThrow();
        var job = source.accept(new io.github.dmytroha.tgconnector.domain.source.TelegramMessageId(2),
                MessageContent.text("Склад, 160 Kč #склад #логистика"), null, clock.instant(), clock).orElseThrow();

        var noAds = new MessageCriteria(Optional.empty(), Set.of(), Optional.empty(), Set.of(), Set.of("#Реклама"), 10);
        var warehouse = new MessageCriteria(Optional.empty(), Set.of(), Optional.empty(), Set.of("склад", "кухня"), Set.of(), 10);

        assertThat(noAds.matches(ad)).isFalse();
        assertThat(noAds.matches(job)).isTrue();
        assertThat(warehouse.matches(job)).isTrue();
        assertThat(warehouse.matches(ad)).isFalse();
    }
}
