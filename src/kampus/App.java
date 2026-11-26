package kampus;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import kampus.ui.MahasiswaFrame;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception exception) {
                System.err.println("Nimbus tidak tersedia; memakai tema Swing bawaan.");
            }
            MahasiswaFrame frame = new MahasiswaFrame();
            frame.setVisible(true);
            frame.initializeDatabase();
        });
    }
}
