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

package io.github.mzmine.gui;

import io.github.mzmine.main.MZmineCore;
import java.util.logging.Logger;

/**
 * Offline-compatible replacement for the upstream network version check.
 *
 * <p>Automatic checks are intentionally disabled so application startup and batch processing never
 * depend on internet access. The class and enum are retained for source compatibility.</p>
 */
public class NewVersionCheck implements Runnable {

  public enum CheckType {
    DESKTOP, MENU
  }

  private static final Logger logger = Logger.getLogger(NewVersionCheck.class.getName());
  private final CheckType checkType;

  public NewVersionCheck(final CheckType type) {
    checkType = type;
  }

  @Override
  public void run() {
    if (checkType == CheckType.MENU) {
      final String message =
          "Automatic update checks are disabled in the open offline fork. "
              + "Review releases manually when internet access is available.";
      logger.info(message);
      MZmineCore.getDesktop().displayMessage(message);
    }
  }
}
