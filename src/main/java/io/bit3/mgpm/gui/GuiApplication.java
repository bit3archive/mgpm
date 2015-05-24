package io.bit3.mgpm.gui;

import io.bit3.mgpm.cmd.Args;
import io.bit3.mgpm.config.Config;

import javax.swing.*;

public class GuiApplication {
  private final Args args;
  private final Config config;

  public GuiApplication(Args args, Config config) {
    this.args = args;
    this.config = config;
  }

  public void run() {
    JOptionPane.showMessageDialog(null, "GUI is not implemented yet :-(");
  }
}
