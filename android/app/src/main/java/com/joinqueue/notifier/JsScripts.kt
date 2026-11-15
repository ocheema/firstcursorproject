package com.joinqueue.notifier

object JsScripts {
    /**
     * Finds Join Queue in the live DOM and fires real pointer/mouse/click events
     * (works better with React than a bare .click() alone).
     */
    const val CLICK_JOIN_QUEUE = """
(function() {
  function label(el) {
    return ((el.innerText || el.textContent || '') + ' ' + (el.getAttribute('aria-label') || '')).trim();
  }
  function isJoin(el) {
    return /join\s*queue/i.test(label(el));
  }
  var nodes = Array.from(document.querySelectorAll('button, a, [role="button"], input[type="button"], input[type="submit"]'));
  var el = nodes.find(isJoin);
  if (!el) {
    el = Array.from(document.querySelectorAll('div, span')).find(isJoin);
  }
  if (!el) return 'NOT_FOUND';

  var target = el;
  for (var i = 0; i < 5 && target; i++) {
    if (target.tagName === 'BUTTON' || target.tagName === 'A' || target.getAttribute('role') === 'button') break;
    if (target.parentElement) target = target.parentElement;
  }

  try { target.scrollIntoView({block:'center', inline:'center'}); } catch (e) {}

  var rect = target.getBoundingClientRect();
  var x = rect.left + Math.max(rect.width / 2, 1);
  var y = rect.top + Math.max(rect.height / 2, 1);
  var opts = {bubbles:true, cancelable:true, composed:true, view:window, clientX:x, clientY:y, buttons:1};

  ['pointerover','mouseover','pointerdown','mousedown','pointerup','mouseup','click'].forEach(function(type) {
    try {
      if (type.indexOf('pointer') === 0 && window.PointerEvent) {
        target.dispatchEvent(new PointerEvent(type, opts));
      } else {
        target.dispatchEvent(new MouseEvent(type, opts));
      }
    } catch (e) {}
  });
  try { target.click(); } catch (e) {}

  return 'CLICKED:' + label(target).replace(/\s+/g,' ').slice(0, 80);
})();
"""

    /**
     * Reads page state after a click.
     * FAILED_ERROR = failure/error copy visible while Join Queue is still there.
     */
    const val CHECK_STATE = """
(function() {
  function textOf(el) {
    return (el.innerText || el.textContent || '').trim();
  }
  var bodyText = textOf(document.body || document.documentElement);
  var hasJoin = Array.from(document.querySelectorAll('button, a, [role="button"], div, span'))
    .some(function(el) {
      return /join\s*queue/i.test(textOf(el) + ' ' + (el.getAttribute('aria-label') || ''));
    });

  var successPhrase = /leave\s*queue|in\s*the\s*queue|already\s*in\s*(the\s*)?queue|you.?re\s*in\s*(the\s*)?queue|successfully\s*joined|queue\s*joined|waiting\s*in\s*(the\s*)?queue/i.test(bodyText);
  var errorPhrase = /unable to join|could not join|failed to join|try again|not available|queue is full|no slots|unsuccessful|couldn.?t join|something went wrong/i.test(bodyText);

  if (!hasJoin && successPhrase) return 'SUCCESS';
  if (hasJoin && errorPhrase) return 'FAILED_ERROR';
  if (hasJoin) return 'HAS_JOIN';
  if (successPhrase) return 'SUCCESS';
  return 'UNKNOWN';
})();
"""
}