package Utilities;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.micah.eacfbppr.Database;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class GoogleSheetsHandler {
    static final Logger LOGGER = AppLogger.getLogger();
    private static final String APPLICATION_NAME = "eacfbppr";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY);
    private static final String CREDENTIALS_FILE_PATH = "client_secret_460985092671-bfnpalea6diaqavvs3tdhj7i4ragf1dq.apps.googleusercontent.com.json";

    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        FileInputStream in = new FileInputStream(CREDENTIALS_FILE_PATH);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(62576).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public static void updateConferenceData() throws IOException, GeneralSecurityException, SQLException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Sheets service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

        String spreadsheetId = "13G6iopwrreyFHzAkTcbP3jIkajjIgKGtZAw1V85_VJc";
        String range = "Conferences!B2:L";

        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
            LOGGER.warning("No conference data found");
        } else {

            for (int col = 0; col < values.get(0).size(); col++) {
                String conferenceName = (String) values.get(0).get(col);
                LOGGER.fine("Conference: " + conferenceName);
                for (int row = 1; row < values.size(); row++) {
                    if (col < values.get(row).size()) {
                        String teamName = (String) values.get(row).get(col);
                        if (teamName != null && !teamName.isEmpty()) {
                            LOGGER.fine("  Team: " + teamName + " " + row);
                        }
                    }
                }
            }


            // Get the database connection from Database class
            Connection conn = Database.getConnection();
            if (conn == null) {
                throw new SQLException("Unable to connect to the database.");
            }

            // Clear current conference assignments
            PreparedStatement clearStmt = conn.prepareStatement("UPDATE teams SET conference_id = NULL");
            clearStmt.executeUpdate();

            for (int col = 0; col < values.get(0).size(); col++) {
                String conferenceName = (String) values.get(0).get(col);

                // Get conference ID
                PreparedStatement getConfIdStmt = conn.prepareStatement("SELECT conference_id FROM conferences WHERE name = ?");
                getConfIdStmt.setString(1, conferenceName);
                var rs = getConfIdStmt.executeQuery();
                int conferenceId = 0;
                if (rs.next()) {
                    conferenceId = rs.getInt("conference_id");
                }

                for (int row = 1; row < values.size(); row++) {
                    if (col < values.get(row).size()) {
                        String teamName = (String) values.get(row).get(col);

                        if (teamName != null && !teamName.isEmpty()) {
                            // Update team with conference
                            PreparedStatement updateStmt = conn.prepareStatement("UPDATE teams SET conference_id = ? WHERE name = ?");
                            updateStmt.setInt(1, conferenceId);
                            updateStmt.setString(2, teamName);
                            updateStmt.executeUpdate();
                        }
                    }
                }


            }
            conn.close();
        }


    }

    public static void updateTop25Rankings() throws IOException, GeneralSecurityException, SQLException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Sheets service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

        String spreadsheetId = "13G6iopwrreyFHzAkTcbP3jIkajjIgKGtZAw1V85_VJc";
        String range = "Rankings!C6:C30";

        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
            LOGGER.warning("No ranking data found");
        } else {
            List<String> top25Teams = new ArrayList<>();
            for (List<Object> row : values) {
                if (!row.isEmpty()) {
                    String teamName = (String) row.get(0);
                    if (teamName != null && !teamName.isEmpty()) {
                        top25Teams.add(teamName);
                        LOGGER.fine("Ranked Team: " + teamName);
                    }
                }
            }

            // Use the list of top 25 teams as needed
            LOGGER.info("Top 25 Teams: " + top25Teams);



            // Use the list of top 25 teams to update the database
            try (Connection conn = Database.getConnection()) {
                if (conn == null) {
                    throw new SQLException("Unable to connect to the database.");
                }

                // Clear current top 25 rankings
                PreparedStatement clearRankingsStmt = conn.prepareStatement("TRUNCATE TABLE top25_teams");
                clearRankingsStmt.executeUpdate();

                // Insert the new rankings into the top25_teams table
                for (int rank = 0; rank < top25Teams.size(); rank++) {
                    String teamName = top25Teams.get(rank);
                    int teamId = getTeamId(conn, teamName);
                    if (teamId != -1) {
                        PreparedStatement insertRankStmt = conn.prepareStatement(
                                "INSERT INTO top25_teams (team_id, team_rank) VALUES (?, ?)"
                        );
                        insertRankStmt.setInt(1, teamId);
                        insertRankStmt.setInt(2, rank + 1);
                        insertRankStmt.executeUpdate();
                    } else {
                        LOGGER.warning("Team not found in database: " + teamName);
                    }
                }
                // Update the is_ranked column in the Teams table
                PreparedStatement updateRankedStmt = conn.prepareStatement(
                        "UPDATE teams SET is_ranked = CASE WHEN team_id IN (SELECT team_id FROM top25_teams) THEN 1 ELSE 0 END"
                );
                updateRankedStmt.executeUpdate();

                LOGGER.info("Top 25 teams updated in the database.");
            }
        }
    }

    public static int getTeamId(Connection conn, String teamName) throws SQLException {
        PreparedStatement checkTeamStmt = conn.prepareStatement("SELECT team_id FROM teams WHERE name = ?");
        checkTeamStmt.setString(1, teamName);
        ResultSet rs = checkTeamStmt.executeQuery();
        if (rs.next()) {
            return rs.getInt("team_id");
        }
        return -1;
    }
}

