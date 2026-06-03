package com.dreamhouse.trading.ui;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.JTableHeader;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Scales Swing text and table geometry with the main window size.
 */
public final class UIAutoScaler {
    public static final String AUTO_RESIZE_MODE_PROPERTY = "dreamhouse.autoResizeMode";
    private static final String BASE_FONT_PROPERTY = "dreamhouse.baseFont";
    private static final String BASE_SIZE_PROPERTY = "dreamhouse.baseSize";
    private static final int BASE_WIDTH = 1600;
    private static final int BASE_HEIGHT = 900;
    private static final double MIN_SCALE = 0.82;
    private static final double MAX_SCALE = 1.28;

    private UIAutoScaler() {
    }

    public static void install(Window window) {
        if (window == null) {
            return;
        }
        Timer resizeTimer = new Timer(120, event -> apply(window));
        resizeTimer.setRepeats(false);
        window.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeTimer.restart();
            }
        });
        SwingUtilities.invokeLater(() -> apply(window));
    }

    public static void install(JComponent component) {
        if (component == null) {
            return;
        }
        Timer resizeTimer = new Timer(120, event -> apply(component));
        resizeTimer.setRepeats(false);
        component.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeTimer.restart();
            }
        });
        SwingUtilities.invokeLater(() -> apply(component));
    }

    public static void apply(Window window) {
        if (window == null) {
            return;
        }
        double scale = calculateScale(window.getSize());
        scaleComponent(window, scale);
        if (window instanceof javax.swing.JFrame frame) {
            JMenuBar menuBar = frame.getJMenuBar();
            if (menuBar != null) {
                scaleComponent(menuBar, scale);
            }
        }
        window.revalidate();
        window.repaint();
    }

    public static void apply(JComponent component) {
        if (component == null) {
            return;
        }
        double scale = calculateComponentScale(component);
        scaleComponent(component, scale);
        component.revalidate();
        component.repaint();
    }

    private static double calculateScale(Dimension size) {
        if (size == null || size.width <= 0 || size.height <= 0) {
            return 1.0;
        }
        double widthScale = size.getWidth() / BASE_WIDTH;
        double heightScale = size.getHeight() / BASE_HEIGHT;
        double scale = Math.sqrt(widthScale * heightScale);
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    private static double calculateComponentScale(JComponent component) {
        Dimension current = component.getSize();
        if (current == null || current.width <= 0 || current.height <= 0) {
            current = component.getPreferredSize();
        }
        Dimension base = baseSize(component, current);
        if (base.width <= 0 || base.height <= 0) {
            return 1.0;
        }
        double widthScale = current.getWidth() / base.getWidth();
        double heightScale = current.getHeight() / base.getHeight();
        double scale = Math.sqrt(widthScale * heightScale);
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    private static Dimension baseSize(JComponent component, Dimension fallback) {
        Object stored = component.getClientProperty(BASE_SIZE_PROPERTY);
        if (stored instanceof Dimension dimension) {
            return dimension;
        }
        Dimension base = fallback != null && fallback.width > 0 && fallback.height > 0
                ? new Dimension(fallback)
                : new Dimension(640, 420);
        component.putClientProperty(BASE_SIZE_PROPERTY, base);
        return base;
    }

    private static void scaleComponent(Component component, double scale) {
        if (component == null) {
            return;
        }
        scaleFont(component, scale);
        tuneComponent(component, scale);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                scaleComponent(child, scale);
            }
        }
    }

    private static void scaleFont(Component component, double scale) {
        Font font = component.getFont();
        if (font == null) {
            return;
        }
        Font baseFont = font;
        if (component instanceof JComponent jComponent) {
            Object stored = jComponent.getClientProperty(BASE_FONT_PROPERTY);
            if (stored instanceof Font storedFont) {
                baseFont = storedFont;
            } else {
                jComponent.putClientProperty(BASE_FONT_PROPERTY, font);
            }
        }
        float scaledSize = (float) Math.max(9.0, baseFont.getSize2D() * scale);
        component.setFont(baseFont.deriveFont(scaledSize));
    }

    private static void tuneComponent(Component component, double scale) {
        if (component instanceof JTable table) {
            if (table.getRowSorter() == null) {
                table.setAutoCreateRowSorter(true);
            }
            Object autoResizeMode = table.getClientProperty(AUTO_RESIZE_MODE_PROPERTY);
            table.setAutoResizeMode(autoResizeMode instanceof Integer mode
                    ? mode
                    : JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
            table.setRowHeight(Math.max(18, (int) Math.round(22 * scale)));
            JTableHeader header = table.getTableHeader();
            if (header != null) {
                scaleFont(header, scale);
                header.setResizingAllowed(true);
                header.setReorderingAllowed(true);
            }
        } else if (component instanceof JToolBar toolBar) {
            toolBar.setFloatable(false);
        } else if (component instanceof AbstractButton button) {
            button.setMargin(new java.awt.Insets(
                    Math.max(2, (int) Math.round(3 * scale)),
                    Math.max(4, (int) Math.round(8 * scale)),
                    Math.max(2, (int) Math.round(3 * scale)),
                    Math.max(4, (int) Math.round(8 * scale))));
        } else if (component instanceof JComboBox<?> comboBox) {
            Dimension preferred = comboBox.getPreferredSize();
            comboBox.setMaximumSize(new Dimension(
                    Math.max(64, (int) Math.round(preferred.width * Math.min(1.15, scale))),
                    Math.max(22, (int) Math.round(25 * scale))));
        } else if (component instanceof JScrollPane scrollPane) {
            scrollPane.getVerticalScrollBar().setUnitIncrement(Math.max(12, (int) Math.round(16 * scale)));
            scrollPane.getHorizontalScrollBar().setUnitIncrement(Math.max(12, (int) Math.round(16 * scale)));
        } else if (component instanceof JMenu menu) {
            menu.setDelay(120);
        }
    }
}
