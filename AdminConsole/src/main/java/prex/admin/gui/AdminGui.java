package prex.admin.gui;

import com.beust.jcommander.Parameter;
import prex.client.PrexClient;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * Created by jorl17 on 14/06/16.
 */
public class AdminGui {
    private JTextField host;
    private JPanel root;
    private JTextField port;
    private JPanel Coordinator;
    private JTextField predictionContext;
    private JButton markAsStartedButton;
    private JButton markAsStoppedButton;
    private JButton startTrainingButton;
    private JLabel ble;
    private JTextField sampleName;
    private JTextField sampleSrc;
    private JButton addAssociationButton;
    private JButton connectButton;
    private JPanel connectedInfoPanel;
    private JTextField exception;
    private JTextField predictionContextAssociations;

    private PrexClient adminClient;
    private static final Color NOT_CONNECTED_COLOR = new Color(255, 0, 0);
    private static final Color CONNECTED_COLOR     = new Color(0, 255, 0);


    private int[] DEFAULT_T = {2500,    5000,5000,10000};
    private int[] DEFAULT_k = {4,       2,   1,   1};

    public AdminGui() {
        connectButton.addActionListener((e) -> connect());
        markAsStartedButton.addActionListener((e) -> adminClient.startRun());
        markAsStoppedButton.addActionListener((e) -> adminClient.stopRun());
        startTrainingButton.addActionListener((e) -> adminClient.startTraining(predictionContext.getText(), exception.getText(), DEFAULT_T, DEFAULT_k));
        addAssociationButton.addActionListener((e) -> adminClient.addPredictionContextIDs(predictionContextAssociations.getText(), new String[][] { new String[] {sampleName.getText(), sampleSrc.getText()}}));
    }

    private void connect() {

        try {
            adminClient = new PrexClient("ADMIN", host.getText(), Integer.valueOf(port.getText()));
            connectedInfoPanel.setBackground(CONNECTED_COLOR);
        } catch (IOException e) {
            e.printStackTrace();
            adminClient = null;
            connectedInfoPanel.setBackground(NOT_CONNECTED_COLOR);
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("AdminGui");
        frame.setContentPane(new AdminGui().root);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }
}
