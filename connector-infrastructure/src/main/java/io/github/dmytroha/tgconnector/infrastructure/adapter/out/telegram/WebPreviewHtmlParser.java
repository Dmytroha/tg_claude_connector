package io.github.dmytroha.tgconnector.infrastructure.adapter.out.telegram;

import io.github.dmytroha.tgconnector.application.dto.IncomingMessage;
import io.github.dmytroha.tgconnector.domain.message.Attachment;
import io.github.dmytroha.tgconnector.domain.message.Author;
import io.github.dmytroha.tgconnector.domain.message.MessageContent;
import io.github.dmytroha.tgconnector.domain.source.TelegramMessageId;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Anti-corruption layer: turns the HTML of {@code https://t.me/s/<channel>} into domain messages.
 */
class WebPreviewHtmlParser {

    private static final Pattern BACKGROUND_URL = Pattern.compile("background-image:url\\('(.+?)'\\)");

    List<IncomingMessage> parse(String html) {
        return Jsoup.parse(html).select("div.tgme_widget_message[data-post]").stream()
                .map(this::parseMessage)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(IncomingMessage::messageId))
                .toList();
    }

    private Optional<IncomingMessage> parseMessage(Element post) {
        var dataPost = post.attr("data-post");
        var slash = dataPost.lastIndexOf('/');
        var time = post.selectFirst(".tgme_widget_message_date time[datetime]");
        if (slash < 0 || time == null) {
            return Optional.empty();
        }
        var text = text(post);
        var attachments = attachments(post);
        if (text.isBlank() && attachments.isEmpty()) {
            return Optional.empty();
        }
        var owner = post.selectFirst(".tgme_widget_message_owner_name");
        var author = new Author(owner != null ? owner.text() : dataPost.substring(0, slash), dataPost.substring(0, slash));
        return Optional.of(new IncomingMessage(
                new TelegramMessageId(Long.parseLong(dataPost.substring(slash + 1))),
                new MessageContent(text, attachments),
                author,
                OffsetDateTime.parse(time.attr("datetime")).toInstant()));
    }

    private String text(Element post) {
        var textElement = post.selectFirst(".tgme_widget_message_text");
        if (textElement == null) {
            return "";
        }
        var copy = textElement.clone();
        copy.select("br").forEach(br -> br.replaceWith(new TextNode("\n")));
        return copy.wholeText().strip();
    }

    private List<Attachment> attachments(Element post) {
        var result = new ArrayList<Attachment>();
        for (var photo : post.select("a.tgme_widget_message_photo_wrap[style]")) {
            var matcher = BACKGROUND_URL.matcher(photo.attr("style"));
            if (matcher.find()) {
                result.add(new Attachment(Attachment.Kind.PHOTO, matcher.group(1)));
            }
        }
        for (var video : post.select("video.tgme_widget_message_video[src]")) {
            result.add(new Attachment(Attachment.Kind.VIDEO, video.attr("src")));
        }
        for (var document : post.select(".tgme_widget_message_document_title")) {
            result.add(new Attachment(Attachment.Kind.DOCUMENT, document.text()));
        }
        return result;
    }
}
