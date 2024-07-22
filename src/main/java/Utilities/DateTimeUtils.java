package Utilities;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Objects;
import java.util.logging.Logger;

public class DateTimeUtils {
    private static final Logger LOGGER = AppLogger.getLogger();


    public static String reverseDate(String date)   {
        LOGGER.fine("Reversing date: " + date);

        if(date.length() != 10)   {
            LOGGER.warning("Received malformed date. Returning original string");
            return date;
        }


        String year = date.substring(0,4);
        String month = date.substring(5,7);
        String day = date.substring(8,10);


        return month + "/" + day + "/" + year;

    }

    //Find the date for the requested game score or info
    public static String findDate(SlashCommandInteractionEvent event)   {
        LOGGER.fine("Grabbing date");
        String date;

        //if a date is provided, grab the included date, otherwise the date will be set as the current date
        if (event.getOption("date") != null) {

            date = dateFromCommand(Objects.requireNonNull(event.getOption("date")).getAsString());

        }
        else  {
            date = dateFromCurrentDay();
        }

        return date;
    }

    public static String dateFromCommand(String dateFromCommand)   {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try   {
            LocalDate.parse(dateFromCommand, formatter);
            return dateFromCommand;
        }
        catch(DateTimeParseException e)   {
            LOGGER.warning("Invalid date provided from");
            return "invalid";
        }

    }

    public static String dateFromCurrentDay()   {
        LOGGER.fine("getting the date from the current day");
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        return now.format(formatter);
    }

    public static String convertUTCtoET(String UTCTime) {
        LOGGER.fine("Converting UTC to ET");
        ZonedDateTime startTimeUTC = ZonedDateTime.parse(UTCTime);
        ZonedDateTime startTimeET = startTimeUTC.withZoneSameInstant(ZoneId.of("America/New_York"));

        return startTimeET.format(DateTimeFormatter.ofPattern("h:mm a"));
    }

    public static String removeDateDash(String date)   {
        LOGGER.fine("Removing - from " + date);
        return date.replaceAll("-","");
    }


    public static String calcDate(long millisecs) {
        SimpleDateFormat date_format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date resultdate = new Date(millisecs);
        return date_format.format(resultdate);
    }

}