/**
 * TSID id generation. {@link com.example.commerce.web.tsid.TsidGenerator#newId()} returns a 13-char
 * Crockford base32 string that is fixed-width and time-sortable, so {@code ORDER BY id} and
 * {@code WHERE id > :cursor} give stable keyset pagination over VARCHAR id columns.
 */
@org.jspecify.annotations.NullMarked
package com.example.commerce.web.tsid;
