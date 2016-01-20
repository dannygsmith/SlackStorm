package com.bryansharpe.slackstorm;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.CaretState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Created by bsharpe on 11/2/2015.
 */
public class SlackPost extends ActionGroup {
    @NotNull
    @Override
    public AnAction[] getChildren(AnActionEvent anActionEvent) {

        // Get settings
        SlackStorage slackStorage = SlackStorage.getInstance();
        final AnAction[] children = new AnAction[slackStorage.settings.size()];

        // Create a new action for each Channel config
        int count = 0;
        for (String key : slackStorage.settings.keySet()) {
            children[count] = new SlackMessage(key);
            count++;
        }

        return children;
    }

    /**
     * Sends the message to the slack channel based on the config
     * settings.
     */
    public class SlackMessage extends AnAction {
        private static final String UTF_8 = "UTF-8";
        private static final String SLACK_ENDPOINT = "https://hooks.slack.com/services/";
        private String token;
        private String channelKey;

        public SlackMessage(String channelKey) {
            super(channelKey);
            this.channelKey = channelKey;
        }

        @Override
        public void update(final AnActionEvent e) {
            //Get required data keys
            final Project project = e.getData(CommonDataKeys.PROJECT);
            final Editor editor = e.getData(CommonDataKeys.EDITOR);

            // Get settings
            SlackStorage slackSettings = SlackStorage.getInstance();

            //Set visibility only in case of existing project and editor and if some text in the editor is selected
            e.getPresentation().setVisible((project != null && editor != null
                    && editor.getSelectionModel().hasSelection()
                    && slackSettings.settings.size() > 0));
        }

        public void actionPerformed(AnActionEvent anActionEvent) {
            //Get all the required data from data keys
            final Editor editor = anActionEvent.getRequiredData(CommonDataKeys.EDITOR);
            final Project project = anActionEvent.getRequiredData(CommonDataKeys.PROJECT);
            final Document document = editor.getDocument();
            final VirtualFile currentFile = FileDocumentManager.getInstance().getFile(document);
            final SelectionModel selectionModel = editor.getSelectionModel();

            String selectedText = selectionModel.getSelectedText();
            if (selectedText == null) {
                return;
            }

            // Get details
            String fileName = currentFile.getName();
            CaretState selectionInfo = editor.getCaretModel().getCaretsAndSelections().get(0);
            int selectionStart = selectionInfo.getSelectionStart().line + 1;
            int selectionEnd = selectionInfo.getSelectionEnd().line + 1;
            String fileDetails = "File: " + fileName + ", Line(s): " + selectionStart + "-" + selectionEnd;

            try {
                this.pushMessage(selectedText, fileDetails, anActionEvent);
            } catch (IOException e) {
                e.printStackTrace();
            }
            selectionModel.removeSelection();
        }

        private void pushMessage(String message, String details, final AnActionEvent actionEvent) throws IOException {
            final Project project = actionEvent.getRequiredData(CommonDataKeys.PROJECT);

            // Reload our settings
            SlackStorage slackSettings = SlackStorage.getInstance();
            this.token = slackSettings.settings.get(this.channelKey);
            String alias = slackSettings.aliases.get(this.channelKey);

            // Fallback from previous versions
            if (alias == null || alias.isEmpty()) {
                alias = "SlackStorm";
            }

            // Simple escape @todo: check against slack input options
            message = message.replace("\"", "\\\"");

            String payload =
                    "{" +
                        "\"attachments\" : [{" +
                        "\"title\" : \"" + details + "\"," +
                        "\"text\" : \"```" + message + "```\"," +
                        "\"mrkdwn_in\" : [\"title\", \"text\"]" +
                        "}]," +
                        "\"username\" : \"" + alias + "\"," +
                        "\"icon_emoji\" : \":thunder_cloud_and_rain:\"" +
                    "}";
            String input = "payload=" + URLEncoder.encode(payload, "UTF-8");

            try {
                URL url = new URL(SLACK_ENDPOINT + this.token);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoInput(true);
                conn.setDoOutput(true);



                DataOutputStream wr = new DataOutputStream (conn.getOutputStream ());
                wr.writeBytes (input);
                wr.flush ();
                wr.close ();

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    String responseMessage = readInputStreamToString(conn);
                    if (responseMessage.equals("ok")) {
                        Messages.showMessageDialog(project, "Message Sent.", "Information", IconLoader.getIcon("/icons/slack.png"));
                    }
                    else {
                        Messages.showMessageDialog(project, "An Error Occurred. Message not sent.", "Error", Messages.getErrorIcon());
                    }
                }
                else {
                    Messages.showMessageDialog(project, "Bad request to Slack.", "Error", Messages.getErrorIcon());
                }

            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        /**
         * @param connection object; note: before calling this function,
         *   ensure that the connection is already be open, and any writes to
         *   the connection's output stream should have already been completed.
         * @return String containing the body of the connection response or
         *   null if the input stream could not be read correctly
         */
        private String readInputStreamToString(HttpURLConnection connection) {
            String result = null;
            StringBuffer sb = new StringBuffer();
            InputStream is = null;

            try {
                is = new BufferedInputStream(connection.getInputStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String inputLine = "";
                while ((inputLine = br.readLine()) != null) {
                    sb.append(inputLine);
                }
                result = sb.toString();
            }
            catch (Exception e) {
                result = null;
            }

            return result;
        }
    }

}
