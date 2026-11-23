/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024-2026 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.glypdl.android.service.browser

object BrowserScripts {

    const val JS_INTERFACE_NAME = "GlypdlMediaDetector"

    val INJECTION_SCRIPT = """
        (function() {
            if (window.__glypdl_sniffer_installed__) return;
            window.__glypdl_sniffer_installed__ = true;

            var lastReportedHref = window.location.href;

            function findActiveVideo() {
                var videos = document.querySelectorAll('video');
                if (!videos || videos.length === 0) return null;
                for (var i = 0; i < videos.length; i++) {
                    var v = videos[i];
                    if (!v.paused && !v.ended && v.currentTime > 0) {
                        return v;
                    }
                }
                var winH = window.innerHeight || document.documentElement.clientHeight || 800;
                var centerY = winH / 2;
                var best = null;
                var minDist = Infinity;
                for (var j = 0; j < videos.length; j++) {
                    var rect = videos[j].getBoundingClientRect();
                    if (rect.bottom > 50 && rect.top < winH - 50) {
                        var dist = Math.abs((rect.top + rect.bottom) / 2 - centerY);
                        if (dist < minDist) {
                            minDist = dist;
                            best = videos[j];
                        }
                    }
                }
                return best || videos[videos.length - 1];
            }

            function notifyUrlChange() {
                try {
                    var current = window.location.href;
                    if (current && current !== lastReportedHref) {
                        lastReportedHref = current;
                        var activeV = findActiveVideo();
                        var currentTitle = extractPageTitle(activeV);
                        if (window.$JS_INTERFACE_NAME && typeof window.$JS_INTERFACE_NAME.postMessage === 'function') {
                            window.$JS_INTERFACE_NAME.postMessage(JSON.stringify({t: 'url', url: current, title: currentTitle || document.title || ''}));
                        }
                        scanElements();
                    }
                } catch(e) {}
            }

            // Hook history.pushState (used by YouTube Shorts, Instagram Reels, Twitter, TikTok, etc.)
            var origPushState = history.pushState;
            history.pushState = function() {
                var res = origPushState.apply(this, arguments);
                notifyUrlChange();
                return res;
            };

            // Hook history.replaceState
            var origReplaceState = history.replaceState;
            history.replaceState = function() {
                var res = origReplaceState.apply(this, arguments);
                notifyUrlChange();
                return res;
            };

            window.addEventListener('popstate', notifyUrlChange);
            window.addEventListener('hashchange', notifyUrlChange);
            window.addEventListener('scrollend', notifyUrlChange);
            window.addEventListener('touchend', function() {
                setTimeout(notifyUrlChange, 200);
                setTimeout(notifyUrlChange, 600);
            });

            // Failsafe timer for dynamic single-page applications like YouTube Shorts
            setInterval(notifyUrlChange, 750);

            var IGNORED_ACCOUNTS = ['null', 'undefined', 'notifications', 'explore', 'messages', 'direct', 'home', 'search', 'reels', 'stories', 'login', 'accounts', 'instagram', 'video'];

            function isCleanUser(u) {
                if (!u || typeof u !== 'string') return false;
                var clean = u.trim().toLowerCase().replace(/^@/, '');
                if (clean.length < 2 || clean.length > 35) return false;
                if (IGNORED_ACCOUNTS.indexOf(clean) !== -1) return false;
                if (clean.indexOf(' ') !== -1) return false;
                return true;
            }

            function extractAccount(elem) {
                try {
                    var isIg = window.location.hostname.indexOf('instagram.com') !== -1;
                    var p = window.location.pathname;
                    var sm = p.match(/\/stories\/([^\/?#]+)/);
                    if (sm && isCleanUser(sm[1])) return sm[1];

                    // Check scoped container first for active video's author
                    if (elem) {
                        var art = elem.closest('article, section, div[role="dialog"], div[role="presentation"], ytm-reel-player-renderer, .reel-player-renderer') || elem.parentElement;
                        if (art) {
                            var links = art.querySelectorAll('a[role="link"][href^="/"], header a[href^="/"], a[href^="/"]');
                            for (var k = 0; k < links.length; k++) {
                                var href = links[k].getAttribute('href') || '';
                                var m = href.match(/^\/([a-zA-Z0-9._]+)\/?$/);
                                if (m && isCleanUser(m[1])) return m[1];
                                var linkText = links[k].textContent ? links[k].textContent.trim().replace(/^@/, '') : '';
                                if (linkText && isCleanUser(linkText)) return linkText;
                            }
                        }
                    }

                    // Only use og:title if NOT in an Instagram SPA reels/stories view (since og:title is static and never updates)
                    var rm = p.match(/\/(?:reel|reels|p)\/([^\/?#]+)/);
                    if (rm && !isIg) {
                        var ogT = document.querySelector('meta[property="og:title"]');
                        if (ogT && ogT.content) {
                            var author = ogT.content.split(' on Instagram')[0];
                            if (author && isCleanUser(author)) return author.trim();
                        }
                    }

                    var ytChan = document.querySelector('ytm-reel-player-header-renderer .reel-player-header-channel-title, .shorts-author-name, [class*="channel-title"]');
                    if (ytChan && ytChan.textContent) {
                        var ytU = ytChan.textContent.trim().replace(/^@/, '');
                        if (isCleanUser(ytU)) return ytU;
                    }

                    var tm = p.match(/\/@([^\/?#]+)/);
                    if (tm && isCleanUser(tm[1])) return tm[1];
                } catch(e) {}
                return null;
            }

            function extractPoster(elem) {
                try {
                    var isIg = window.location.hostname.indexOf('instagram.com') !== -1;
                    var isReelsOrStories = isIg && (window.location.pathname.indexOf('/reel') !== -1 || window.location.pathname.indexOf('/stories') !== -1);
                    if (elem) {
                        if (elem.poster) return elem.poster;
                        if (elem.getAttribute && elem.getAttribute('poster')) return elem.getAttribute('poster');
                        var container = elem.closest('article, section, div[role="dialog"], div[role="presentation"], ytm-reel-player-renderer, .reel-player-renderer') || elem.parentElement;
                        if (container) {
                            var imgs = container.querySelectorAll('img[src*="fbcdn"], img[src*="cdninstagram"], img[src^="http"]');
                            for (var i = 0; i < imgs.length; i++) {
                                var src = imgs[i].src || '';
                                if (src.startsWith('data:') || src.indexOf('1x1') !== -1) continue;
                                var w = imgs[i].naturalWidth || imgs[i].clientWidth || 0;
                                var h = imgs[i].naturalHeight || imgs[i].clientHeight || 0;
                                if (w > 80 || h > 80 || (!w && !h && (src.indexOf('/v/t51.') !== -1 || src.indexOf('_n.jpg') !== -1))) {
                                    return src;
                                }
                            }
                            if (imgs.length > 0 && !imgs[0].src.startsWith('data:')) {
                                return imgs[0].src;
                            }
                        }
                    }
                    // NEVER fall back to static og:image on Instagram Reels/Stories SPA navigation
                    if (!isReelsOrStories) {
                        var og = document.querySelector('meta[property="og:image"], meta[name="twitter:image"]');
                        if (og && og.content) return og.content;
                    }
                } catch(e) {}
                return null;
            }

            function extractPageTitle(elem) {
                try {
                    var isIg = window.location.hostname.indexOf('instagram.com') !== -1;
                    var isReelsOrStories = isIg && (window.location.pathname.indexOf('/reel') !== -1 || window.location.pathname.indexOf('/stories') !== -1);
                    if (isReelsOrStories) {
                        var acc = extractAccount(elem);
                        if (acc) {
                            return '@' + acc + ' • Reel';
                        }
                    }
                    var ytShorts = document.querySelector('ytm-reel-player-header-renderer .reel-player-header-title, .reel-player-header-title, h2.shorts-title, [class*="shorts-title"]');
                    if (ytShorts && ytShorts.textContent && ytShorts.textContent.trim()) {
                        return ytShorts.textContent.trim();
                    }
                    if (!isReelsOrStories) {
                        var ogTitle = document.querySelector('meta[property="og:title"]');
                        if (ogTitle && ogTitle.content && ogTitle.content.trim()) {
                            return ogTitle.content.trim();
                        }
                    }
                } catch(e) {}
                return document.title || '';
            }

            function sendCandidate(url, poster, mime, isBlob, elem) {
                if (!url || typeof url !== 'string') return;
                var trimmed = url.trim();
                if (!trimmed || trimmed.startsWith('data:') || trimmed.length > 2048) return;
                try {
                    var isPlatformBlob = isBlob && (
                        window.location.hostname.indexOf('instagram.com') !== -1 ||
                        window.location.hostname.indexOf('youtube.com') !== -1 ||
                        window.location.hostname.indexOf('tiktok.com') !== -1
                    );
                    var candidateTarget = isPlatformBlob ? window.location.href : trimmed;
                    var absoluteUrl = new URL(candidateTarget, window.location.href).href;
                    var resolvedPoster = poster || extractPoster(elem);
                    var payload = JSON.stringify({
                        url: absoluteUrl,
                        poster: resolvedPoster ? new URL(resolvedPoster, window.location.href).href : null,
                        mime: mime || (isPlatformBlob ? "video/mp4" : null),
                        title: extractPageTitle(elem),
                        account: extractAccount(elem),
                        isBlob: isPlatformBlob ? false : !!isBlob
                    });
                    if (window.$JS_INTERFACE_NAME && typeof window.$JS_INTERFACE_NAME.postMessage === 'function') {
                        window.$JS_INTERFACE_NAME.postMessage(JSON.stringify({t: 'media', payload: payload}));
                    }
                } catch (e) {}
            }

            function scanElements() {
                try {
                    var activeVideo = findActiveVideo();

                    // Instagram Story / Reel / Post: promote page URL when active video is present
                    if (window.location.hostname.indexOf('instagram.com') !== -1) {
                        var path = window.location.pathname;
                        if (path.indexOf('/stories/') !== -1 || path.indexOf('/reel/') !== -1 || path.indexOf('/reels/') !== -1 || path.indexOf('/p/') !== -1) {
                            if (activeVideo) {
                                sendCandidate(window.location.href, extractPoster(activeVideo), "video/mp4", false, activeVideo);
                            }
                        }
                    }

                    // Video elements
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        var poster = extractPoster(v);
                        if (v.src) {
                            sendCandidate(v.src, poster, null, v.src.startsWith('blob:'), v);
                        }
                        if (v.currentSrc && v.currentSrc !== v.src) {
                            sendCandidate(v.currentSrc, poster, null, v.currentSrc.startsWith('blob:'), v);
                        }
                        var sources = v.querySelectorAll('source');
                        for (var j = 0; j < sources.length; j++) {
                            if (sources[j].src) {
                                sendCandidate(sources[j].src, poster, sources[j].type, sources[j].src.startsWith('blob:'), v);
                            }
                        }
                    }

                    // Audio elements
                    var audios = document.querySelectorAll('audio');
                    for (var k = 0; k < audios.length; k++) {
                        var a = audios[k];
                        if (a.src) {
                            sendCandidate(a.src, null, null, a.src.startsWith('blob:'), a);
                        }
                        if (a.currentSrc && a.currentSrc !== a.src) {
                            sendCandidate(a.currentSrc, null, null, a.currentSrc.startsWith('blob:'), a);
                        }
                        var aSources = a.querySelectorAll('source');
                        for (var l = 0; l < aSources.length; l++) {
                            if (aSources[l].src) {
                                sendCandidate(aSources[l].src, null, aSources[l].type, aSources[l].src.startsWith('blob:'), a);
                            }
                        }
                    }

                    // OpenGraph meta tags
                    var metaVideo = document.querySelector('meta[property="og:video"], meta[property="og:video:url"], meta[name="twitter:player:stream"]');
                    if (metaVideo && metaVideo.content) {
                        sendCandidate(metaVideo.content, null, null, false);
                    }
                    var metaAudio = document.querySelector('meta[property="og:audio"], meta[property="og:audio:url"]');
                    if (metaAudio && metaAudio.content) {
                        sendCandidate(metaAudio.content, null, null, false);
                    }
                } catch (e) {}
            }

            // Hook XMLHttpRequest.open
            var origOpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url) {
                try {
                    if (url && typeof url === 'string') {
                        var lower = url.toLowerCase();
                        if (lower.indexOf('.m3u8') !== -1 || lower.indexOf('.mpd') !== -1 ||
                            lower.indexOf('.mp4') !== -1 || lower.indexOf('.webm') !== -1 ||
                            lower.indexOf('.mp3') !== -1 || lower.indexOf('.m4a') !== -1) {
                            sendCandidate(url, null, null, false);
                        }
                    }
                } catch (e) {}
                return origOpen.apply(this, arguments);
            };

            // Hook window.fetch
            if (window.fetch) {
                var origFetch = window.fetch;
                window.fetch = function() {
                    try {
                        var input = arguments[0];
                        var fetchUrl = typeof input === 'string' ? input : (input && input.url ? input.url : null);
                        if (fetchUrl) {
                            var lower = fetchUrl.toLowerCase();
                            if (lower.indexOf('.m3u8') !== -1 || lower.indexOf('.mpd') !== -1 ||
                                lower.indexOf('.mp4') !== -1 || lower.indexOf('.webm') !== -1 ||
                                lower.indexOf('.mp3') !== -1 || lower.indexOf('.m4a') !== -1) {
                                sendCandidate(fetchUrl, null, null, false);
                            }
                        }
                    } catch (e) {}
                    return origFetch.apply(this, arguments);
                };
            }

            // Initial scan & MutationObserver for dynamic DOM changes
            scanElements();
            if (window.MutationObserver) {
                var observer = new MutationObserver(function() {
                    notifyUrlChange();
                    scanElements();
                });
                if (document.body) {
                    observer.observe(document.body, { childList: true, subtree: true });
                } else {
                    document.addEventListener('DOMContentLoaded', function() {
                        if (document.body) {
                            observer.observe(document.body, { childList: true, subtree: true });
                        }
                    });
                }
            }
        })();
    """.trimIndent()
}
