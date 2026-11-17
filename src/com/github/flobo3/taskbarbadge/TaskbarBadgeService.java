package com.github.flobo3.taskbarbadge;

import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JFrame;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service(Service.Level.APP)
public final class TaskbarBadgeService {
    private static final Logger LOG = Logger.getInstance(TaskbarBadgeService.class);
    private static final int CANVAS_SIZE = 32;
    private final Map<Project, String> myAppliedKeys = new ConcurrentHashMap<>();

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

        Image badge = settings.isEnabled() ? resolveBadgeImage(project, settings) : null;
        try {
            Taskbar.getTaskbar().setWindowIconBadge(frame, badge);
            myAppliedKeys.put(project, key);
        } catch (Throwable t) {
            LOG.warn("Failed to update taskbar overlay icon for project: " + project.getName(), t);
        }
    }

    private static @Nullable Image resolveBadgeImage(@NotNull Project project, @NotNull TaskbarBadgeSettings settings) {
        int badgeSize = settings.getBadgeSize();
        String basePath = project.getBasePath();
        if (basePath == null) {
            return null;
        }

        Image pngIcon = loadCustomPng(basePath);
        if (pngIcon != null) {
            return placeInBottomRightCorner(pngIcon, CANVAS_SIZE, badgeSize);
        }

        boolean hasCustom = hasCustomIconFile(basePath);
        if (!hasCustom && !settings.isFallbackToProjectLetter()) {
            return null;
        }

        try {
            RecentProjectsManagerBase manager = RecentProjectsManagerBase.getInstanceEx();
            Path path = Paths.get(basePath);
            Icon projectIcon = manager.getProjectIcon(path, true, badgeSize, project.getName());
            if (projectIcon != null) {
                BufferedImage rendered = renderSwingIcon(projectIcon, badgeSize);
                return placeInBottomRightCorner(rendered, CANVAS_SIZE, badgeSize);
            }
        } catch (Throwable t) {
            LOG.warn("Failed to resolve project icon via RecentProjectsManagerBase", t);
        }

        return null;
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

    private static @Nullable Image loadCustomPng(@NotNull String basePath) {
        String[] candidates = {".idea/icon_dark.png", ".idea/icon.png"};
        for (String rel : candidates) {
            File file = new File(basePath, rel);
            if (file.isFile()) {
                try {
                    Image img = ImageIO.read(file);
                    if (img != null) {
                        return img;
                    }
                } catch (Throwable t) {
                    LOG.warn("Failed to read project PNG icon: " + file, t);
                }
            }
        }
        return null;
    }

    private static @NotNull BufferedImage renderSwingIcon(@NotNull Icon icon, int targetSize) {
        int w = Math.max(1, icon.getIconWidth());
        int h = Math.max(1, icon.getIconHeight());
        BufferedImage raw = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = raw.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        icon.paintIcon(null, g, 0, 0);
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
