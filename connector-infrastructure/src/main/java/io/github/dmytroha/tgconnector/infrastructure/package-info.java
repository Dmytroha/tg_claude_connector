/**
 * Infrastructure layer: adapters that connect the application ports to the outside world.
 * <ul>
 *     <li>{@code adapter.in.*} — driving adapters (Telegram bot updates, REST API, scheduler) calling use cases;</li>
 *     <li>{@code adapter.out.*} — driven adapters implementing ports and repositories (Telegram feed, storage, events).</li>
 * </ul>
 * Adapters never call each other directly — only through application ports.
 */
package io.github.dmytroha.tgconnector.infrastructure;
