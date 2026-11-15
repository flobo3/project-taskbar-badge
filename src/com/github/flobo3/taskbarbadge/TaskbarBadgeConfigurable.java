package com.github.flobo3.taskbarbadge;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;

public final class TaskbarBadgeConfigurable implements SearchableConfigurable {

    private JPanel myPanel;
    private JCheckBox myEnabledCheckBox;
    private JCheckBox myFallbackLetterCheckBox;
    private JSpinner myBadgeSizeSpinner;

    @Override
    public @NotNull String getId() {
        return "com.github.flobo3.projectTaskbarBadge.settings";
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Project Taskbar Badge";
    }

    @Override
    public @Nullable JComponent createComponent() {
        myEnabledCheckBox = new JCheckBox("Enable project taskbar badge");
        myFallbackLetterCheckBox = new JCheckBox("Show project letter badge in project color when no custom icon is set");
        myBadgeSizeSpinner = new JSpinner(new SpinnerNumberModel(24, 16, 32, 2));

        myEnabledCheckBox.addActionListener(e -> updateControlsState());

        JPanel subOptions = FormBuilder.createFormBuilder()
                .addComponent(myFallbackLetterCheckBox)
                .addLabeledComponent("Badge size (px, 16-32):", myBadgeSizeSpinner)
                .getPanel();
        subOptions.setBorder(new EmptyBorder(4, 22, 0, 0));

        myPanel = new JPanel(new BorderLayout());
        JPanel root = FormBuilder.createFormBuilder()
                .addComponent(myEnabledCheckBox)
                .addComponent(subOptions)
                .getPanel();
        myPanel.add(root, BorderLayout.NORTH);

        reset();
        return myPanel;
    }

    private void updateControlsState() {
        boolean enabled = myEnabledCheckBox.isSelected();
        myFallbackLetterCheckBox.setEnabled(enabled);
        myBadgeSizeSpinner.setEnabled(enabled);
    }

    @Override
    public boolean isModified() {
        TaskbarBadgeSettings settings = TaskbarBadgeSettings.getInstance();
        return myEnabledCheckBox.isSelected() != settings.isEnabled()
                || myFallbackLetterCheckBox.isSelected() != settings.isFallbackToProjectLetter()
                || ((Number) myBadgeSizeSpinner.getValue()).intValue() != settings.getBadgeSize();
    }

    @Override
    public void apply() {
        TaskbarBadgeSettings settings = TaskbarBadgeSettings.getInstance();
        settings.setEnabled(myEnabledCheckBox.isSelected());
        settings.setFallbackToProjectLetter(myFallbackLetterCheckBox.isSelected());
        settings.setBadgeSize(((Number) myBadgeSizeSpinner.getValue()).intValue());
        TaskbarBadgeService.getInstance().refreshAllOpenProjects();
    }

    @Override
    public void reset() {
        TaskbarBadgeSettings settings = TaskbarBadgeSettings.getInstance();
        myEnabledCheckBox.setSelected(settings.isEnabled());
        myFallbackLetterCheckBox.setSelected(settings.isFallbackToProjectLetter());
        myBadgeSizeSpinner.setValue(settings.getBadgeSize());
        updateControlsState();
    }

    @Override
    public void disposeUIResources() {
        myPanel = null;
        myEnabledCheckBox = null;
        myFallbackLetterCheckBox = null;
        myBadgeSizeSpinner = null;
    }
}
