package app.form;

import app.service.SettingsService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class ConfigForm extends JDialog {

    private static final long serialVersionUID = 1L;

    private final transient SettingsService settings;
    private final transient JSpinner intervalSpinner;

    public ConfigForm(SettingsService settings) {
        this.settings = settings;
        this.intervalSpinner = buildIntervalSpinner();

        // O tema e aplicado globalmente em ThemeService.install().
        setTitle("Configurações - ECF Notificador");
        setModal(true);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        add(buildContentPanel(), BorderLayout.CENTER);
        add(buildButtonsPanel(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);

        loadSettings();
    }

    private JPanel buildContentPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel intervalLabel = new JLabel("Intervalo de verificação (horas):", SwingConstants.LEFT);
        intervalLabel.setHorizontalAlignment(SwingConstants.LEFT);

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = 0;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(4, 0, 4, 12);
        panel.add(intervalLabel, labelConstraints);

        GridBagConstraints spinnerConstraints = new GridBagConstraints();
        spinnerConstraints.gridx = 1;
        spinnerConstraints.gridy = 0;
        spinnerConstraints.anchor = GridBagConstraints.EAST;
        spinnerConstraints.insets = new Insets(4, 0, 4, 0);
        panel.add(intervalSpinner, spinnerConstraints);

        return panel;
    }

    private JPanel buildButtonsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));

        JButton saveButton = new JButton("Salvar");
        saveButton.setPreferredSize(new Dimension(90, 30));
        saveButton.addActionListener(new SaveButtonListener());

        JButton cancelButton = new JButton("Cancelar");
        cancelButton.setPreferredSize(new Dimension(90, 30));
        cancelButton.addActionListener(new CancelButtonListener());

        panel.add(saveButton);
        panel.add(cancelButton);

        return panel;
    }

    private JSpinner buildIntervalSpinner() {
        SpinnerNumberModel model = new SpinnerNumberModel(6, 1, 168, 1);
        JSpinner spinner = new JSpinner(model);
        spinner.setPreferredSize(new Dimension(70, 24));
        return spinner;
    }

    private void loadSettings() {
        intervalSpinner.setValue(settings.getCheckIntervalHours());
    }

    private class SaveButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            Integer value = (Integer) intervalSpinner.getValue();
            settings.setCheckIntervalHours(value);

            JOptionPane.showMessageDialog(
                    ConfigForm.this,
                    "Configurações salvas! A alteração será aplicada na próxima verificação.",
                    "Sucesso",
                    JOptionPane.INFORMATION_MESSAGE
            );
            dispose();
        }
    }

    private class CancelButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            dispose();
        }
    }
}
