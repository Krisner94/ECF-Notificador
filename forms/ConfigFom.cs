using System;
using System.Drawing;
using System.Windows.Forms;
using ECF_Notificador.services;

namespace ECF_Notificador
{
    public class ConfigForm : Form
    {
        private NumericUpDown numInterval = null!;
        private Button btnSave = null!;
        private Button btnCancel = null!;
        private readonly SettingsService _settings;

        public ConfigForm(SettingsService settings)
        {
            _settings = settings;
            InitializeComponents();

            if (File.Exists(_settings.IconPath))
            {
                this.Icon = new Icon(_settings.IconPath);
            }

            LoadSettings();
        }

        private void InitializeComponents()
        {
            this.Text = "Configurações - ECF Notificador";
            this.Size = new Size(320, 160);
            this.FormBorderStyle = FormBorderStyle.FixedDialog;
            this.MaximizeBox = false;
            this.MinimizeBox = false;
            this.StartPosition = FormStartPosition.CenterScreen;

            var lblInterval = new Label
            {
                Text = "Intervalo de verificação (horas):",
                Location = new Point(12, 20),
                Size = new Size(180, 23)
            };

            numInterval = new NumericUpDown
            {
                Location = new Point(220, 18),
                Size = new Size(60, 23),
                Minimum = 1,
                Maximum = 168
            };

            btnSave = new Button
            {
                Text = "Salvar",
                Location = new Point(120, 75),
                Size = new Size(80, 30)
            };
            btnSave.Click += BtnSave_Click;

            btnCancel = new Button
            {
                Text = "Cancelar",
                Location = new Point(210, 75),
                Size = new Size(80, 30)
            };
            btnCancel.Click += (s, e) => this.Close();

            this.Controls.AddRange(new Control[] { lblInterval, numInterval, btnSave, btnCancel });
        }

        private void LoadSettings()
        {
            numInterval.Value = _settings.CheckIntervalHours;
        }

        private void BtnSave_Click(object? sender, EventArgs e)
        {
            _settings.CheckIntervalHours = (int)numInterval.Value;

            MessageBox.Show("Configurações salvas! A alteração será aplicada na próxima verificação.",
                            "Sucesso", MessageBoxButtons.OK, MessageBoxIcon.Information);
            this.Close();
        }
    }
}