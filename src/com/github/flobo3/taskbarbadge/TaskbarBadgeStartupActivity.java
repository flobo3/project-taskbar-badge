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

            Runnable forceRefresh = () -> ApplicationManager.getApplication()
                    .invokeLater(() -> service.applyForProject(project, true));

            WindowAdapter windowListener = new WindowAdapter() {
                @Override
                public void windowOpened(WindowEvent e) {
                    forceRefresh.run();
                }

                @Override
                public void windowDeiconified(WindowEvent e) {
                    forceRefresh.run();
                }
            };

            frame.addWindowListener(windowListener);

            Disposer.register(project, () -> {
                service.clearCache(project);
                frame.removeWindowListener(windowListener);
            });
        });
    }
}
