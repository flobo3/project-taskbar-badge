package com.github.flobo3.taskbarbadge;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.JFrame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class TaskbarBadgeStartupActivity implements StartupActivity.DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        if (!TaskbarBadgeService.isOverlaySupported()) {
            return;
        }

        TaskbarBadgeService service = TaskbarBadgeService.getInstance();

        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            JFrame frame = WindowManager.getInstance().getFrame(project);
            if (frame == null) {
                return;
            }

            service.applyForProject(project, true);

            Runnable reapply = () -> ApplicationManager.getApplication()
                    .invokeLater(() -> service.reapplyCachedBadge(project));

            WindowAdapter windowListener = new WindowAdapter() {
                @Override
                public void windowOpened(WindowEvent e) {
                    reapply.run();
                }

                @Override
                public void windowDeiconified(WindowEvent e) {
                    reapply.run();
                }

                @Override
                public void windowActivated(WindowEvent e) {
                    reapply.run();
                }

                @Override
                public void windowGainedFocus(WindowEvent e) {
                    reapply.run();
                }

                @Override
                public void windowStateChanged(WindowEvent e) {
                    reapply.run();
                }
            };

            frame.addWindowListener(windowListener);
            frame.addWindowFocusListener(windowListener);
            frame.addWindowStateListener(windowListener);

            Disposer.register(project, () -> {
                service.clearCache(project);
                frame.removeWindowListener(windowListener);
                frame.removeWindowFocusListener(windowListener);
                frame.removeWindowStateListener(windowListener);
            });
        });
    }
}
