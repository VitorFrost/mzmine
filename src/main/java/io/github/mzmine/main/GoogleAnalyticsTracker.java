/*
 * Copyright (c) 2004-2026 The MZmine Development Team and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.mzmine.main;

import io.github.mzmine.modules.MZmineRunnableModule;
import io.github.mzmine.taskcontrol.Task;

/**
 * Compatibility facade retained for source compatibility with MZmine 3.9.
 *
 * <p>The upstream implementation transmitted anonymous usage events over the network. The open
 * offline fork deliberately performs no telemetry and opens no network connection. Keeping the
 * original method signatures avoids invasive changes to modules that report execution events.</p>
 */
public class GoogleAnalyticsTracker {

  public static final GoogleAnalyticsTracker GAT = new GoogleAnalyticsTracker();

  private GoogleAnalyticsTracker() {
  }

  public static void track(final String title, final String url) {
    // Intentionally disabled: this fork does not transmit usage telemetry.
  }

  public static void trackTaskRun(final Task task) {
    // Intentionally disabled: this fork does not transmit usage telemetry.
  }

  public static void trackClass(final String title, final Object obj) {
    // Intentionally disabled: this fork does not transmit usage telemetry.
  }

  public static void trackModule(final MZmineRunnableModule module) {
    // Intentionally disabled: this fork does not transmit usage telemetry.
  }

  public void send(final String pageTitle, final String pageUrl) {
    // Intentionally disabled: this fork does not transmit usage telemetry.
  }
}
