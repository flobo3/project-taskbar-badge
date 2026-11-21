package com.github.flobo3.taskbarbadge;

import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service(Service.Level.APP)
public final class TaskbarBadgeService {
    private static final Logger LOG = Logger.getInstance(TaskbarBadgeService.class);
    private static final int CANVAS_SIZE = 32;
    private final Map<Project, String> myAppliedKeys = new ConcurrentHashMap<>();
    private final Map<Project, Image> myCachedBadges = new ConcurrentHashMap<>();

    public static TaskbarBadgeService getInstance() {
        return ApplicationManager.getApplication().getService(TaskbarBadgeService.class);
    }

    public static boolean isOverlaySupported() {
        return Taskbar.isTaskbarSupported()
                && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_BADGE_IMAGE_WINDOW);
    }

    public void refreshAllOpenProjects() {
        if (!isOverlaySupported()) {
            return;
        }
        myAppliedKeys.clear();
        myCachedBadges.clear();
        ApplicationManager.getApplication().invokeLater(() -> {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (!project.isDisposed()) {
                    applyForProject(project, true);
                }
            }
        });
    }

    public void clearCache(@NotNull Project project) {
        myAppliedKeys.remove(project);
        myCachedBadges.remove(project);
    }

    public void reapplyCachedBadge(@NotNull Project project) {
        if (project.isDisposed() || !isOverlaySupported()) {
            return;
        }
        TaskbarBadgeSettings settings = TaskbarBadgeSettings.getInstance();
        if (!settings.isEnabled()) {
            return;
        }
        Image cached = myCachedBadges.get(project);
        if (cached == null) {
            applyForProject(project, true);
            return;
        }
        JFrame frame = WindowManager.getInstance().getFrame(project);
        if (frame == null || !frame.isDisplayable()) {
            return;
        }
        try {
            Taskbar.getTaskbar().setWindowIconBadge(frame, cached);
        } catch (Throwable t) {
            LOG.warn("Failed to reapply taskbar overlay icon for project: " + project.getName(), t);
        }
    }

    public void applyForProject(@NotNull Project project) {
        applyForProject(project, false);
    }

    public void applyForProject(@NotNull Project project, boolean force) {
        if (project.isDisposed() || !isOverlaySupported()) {
            return;
        }
        JFrame frame = WindowManager.getInstance().getFrame(project);
        if (frame == null || !frame.isDisplayable()) {
            return;
        }

        TaskbarBadgeSettings settings = TaskbarBadgeSettings.getInstance();
        String key = settings.isEnabled() + ":" + settings.isFallbackToProjectLetter() + ":" + settings.getBadgeSize()
                + ":" + project.getBasePath() + ":" + frame.hashCode();
        if (!force && key.equals(myAppliedKeys.get(project))) {
            return;
        }

        if (!settings.isEnabled()) {
            setBadge(project, frame, key, null);
            return;
        }

        resolveAndApplyBadge(project, frame, key, settings);
    }

    private void setBadge(@NotNull Project project, @NotNull JFrame frame, @NotNull String key, @Nullable Image badge) {
        if (project.isDisposed() || !frame.isDisplayable()) {
            return;
        }
        try {
            Taskbar.getTaskbar().setWindowIconBadge(frame, badge);
            myAppliedKeys.put(project, key);
            if (badge != null) {
                myCachedBadges.put(project, badge);
            } else {
                myCachedBadges.remove(project);
            }
        } catch (Throwable t) {
            LOG.warn("Failed to update taskbar overlay icon for project: " + project.getName(), t);
        }
    }

    private void resolveAndApplyBadge(
            @NotNull Project project,
            @NotNull JFrame frame,
            @NotNull String key,
            @NotNull TaskbarBadgeSettings settings
    ) {
        int badgeSize = settings.getBadgeSize();
        String basePath = project.getBasePath();
        if (basePath == null) {
            setBadge(project, frame, key, null);
            return;
        }

        Image customIcon = loadCustomIcon(basePath, badgeSize);
        if (customIcon != null) {
            setBadge(project, frame, key, placeInBottomRightCorner(customIcon, CANVAS_SIZE, badgeSize));
            return;
        }

        boolean hasCustom = hasCustomIconFile(basePath);
        if (!hasCustom && !settings.isFallbackToProjectLetter()) {
            setBadge(project, frame, key, null);
            return;
        }

        try {
            RecentProjectsManagerBase manager = RecentProjectsManagerBase.getInstanceEx();
            // Use the String overload: getProjectIcon(Path, …) only exists since 2025.3, while the
            // String one is present in every supported release (2025.1+). Calling the Path variant
            // broke the plugin on 2025.1/2025.2 with NoSuchMethodError (Plugin Verifier: critical).
            Icon projectIcon = manager.getProjectIcon(basePath, true, badgeSize, project.getName());
            if (projectIcon == null) {
                setBadge(project, frame, key, null);
                return;
            }
            JComponent targetComponent = frame.getRootPane();
            BufferedImage initial = renderSwingIcon(projectIcon, badgeSize, targetComponent);
            if (hasVisiblePixels(initial)) {
                setBadge(project, frame, key, placeInBottomRightCorner(initial, CANVAS_SIZE, badgeSize));
                return;
            }
            final int[] attempts = {0};
            Timer pollTimer = new Timer(100, null);
            pollTimer.addActionListener(e -> {
                attempts[0]++;
                if (project.isDisposed() || !frame.isDisplayable() || attempts[0] > 30) {
                    pollTimer.stop();
                    return;
                }
                BufferedImage rendered = renderSwingIcon(projectIcon, badgeSize, targetComponent);
                if (hasVisiblePixels(rendered)) {
                    pollTimer.stop();
                    setBadge(project, frame, key, placeInBottomRightCorner(rendered, CANVAS_SIZE, badgeSize));
                }
            });
            pollTimer.setRepeats(true);
            pollTimer.start();
        } catch (Throwable t) {
            LOG.warn("Failed to resolve project icon via RecentProjectsManagerBase", t);
        }
    }

    private static boolean hasVisiblePixels(@NotNull BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasCustomIconFile(@NotNull String basePath) {
        String[] files = {".idea/icon_dark.png", ".idea/icon.png", ".idea/icon_dark.svg", ".idea/icon.svg"};
        for (String rel : files) {
            if (new File(basePath, rel).isFile()) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable Image loadCustomIcon(@NotNull String basePath, int targetSize) {
        String[] candidates = {".idea/icon_dark.png", ".idea/icon.png", ".idea/icon_dark.svg", ".idea/icon.svg"};
        for (String rel : candidates) {
            File file = new File(basePath, rel);
            if (!file.isFile()) {
                continue;
            }
            try {
                if (rel.endsWith(".svg")) {
                    Icon icon = IconLoader.findIcon(file.toURI().toURL(), false);
                    if (icon != null) {
                        BufferedImage rendered = renderSwingIcon(icon, targetSize, null);
                        if (hasVisiblePixels(rendered)) {
                            return rendered;
                        }
                    }
                } else {
                    Image img = ImageIO.read(file);
                    if (img != null) {
                        return img;
                    }
                }
            } catch (Throwable t) {
                LOG.warn("Failed to read project icon: " + file, t);
            }
        }
        return null;
    }

    private static @NotNull BufferedImage renderSwingIcon(@NotNull Icon icon, int targetSize, @Nullable JComponent component) {
        int w = Math.max(1, icon.getIconWidth());
        int h = Math.max(1, icon.getIconHeight());
        BufferedImage raw = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = raw.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        icon.paintIcon(component, g, 0, 0);
        g.dispose();

        if (w == targetSize && h == targetSize) {
            return raw;
        }
        BufferedImage scaled = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = scaled.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        sg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        sg.drawImage(raw, 0, 0, targetSize, targetSize, null);
        sg.dispose();
        return scaled;
    }

    private static @NotNull Image placeInBottomRightCorner(@NotNull Image src, int canvasSize, int iconSize) {
        BufferedImage dst = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int offset = canvasSize - iconSize;
        g.drawImage(src, offset, offset, iconSize, iconSize, null);
        g.dispose();
        return dst;
    }
}
