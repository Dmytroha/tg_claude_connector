/**
 * Application layer: orchestrates the domain to fulfil use cases.
 * <ul>
 *     <li>{@code port.in} — use cases (driving ports) called by inbound adapters (REST, bot, scheduler);</li>
 *     <li>{@code port.out} — driven ports implemented by outbound adapters (Telegram clients, event bus);</li>
 *     <li>{@code service} — use case implementations. Framework-free; wired in the bootstrap module.</li>
 * </ul>
 */
package io.github.dmytroha.tgconnector.application;
