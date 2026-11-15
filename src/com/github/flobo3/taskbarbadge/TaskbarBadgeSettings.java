package com.github.flobo3.taskbarbadge;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.APP)
@State(
        name = "ProjectTaskbarBadgeSettings",
        storages = @Storage("projectTaskbarBadge.xml")
)
public final class TaskbarBadgeSettings implements PersistentStateComponent<TaskbarBadgeSettings.State> {

    public static final class State {
        public boolean enabled = true;
        public boolean fallbackToProjectLetter = true;
        public int badgeSize = 24;
    }

    private State myState = new State();

    public static TaskbarBadgeSettings getInstance() {
        return ApplicationManager.getApplication().getService(TaskbarBadgeSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    public boolean isEnabled() {
        return myState.enabled;
    }

    public void setEnabled(boolean enabled) {
        myState.enabled = enabled;
    }

    public boolean isFallbackToProjectLetter() {
        return myState.fallbackToProjectLetter;
    }

    public void setFallbackToProjectLetter(boolean fallbackToProjectLetter) {
        myState.fallbackToProjectLetter = fallbackToProjectLetter;
    }

    public int getBadgeSize() {
        int size = myState.badgeSize;
        return Math.max(16, Math.min(32, size));
    }

    public void setBadgeSize(int badgeSize) {
        myState.badgeSize = Math.max(16, Math.min(32, badgeSize));
    }
}
