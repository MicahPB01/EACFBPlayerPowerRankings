package CommandInteraction.Commands.Audio;

import CommandInteraction.Command;
import CommandInteraction.Commands.Audio.LavaPlayer.GuildMusicManager;
import CommandInteraction.Commands.Audio.LavaPlayer.PlayerManager;
import CommandInteraction.Commands.Audio.LavaPlayer.TrackScheduler;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.managers.AudioManager;

public class Stop implements Command {
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        GuildVoiceState memberVoiceState = member.getVoiceState();

        if(!memberVoiceState.inAudioChannel())   {
            event.reply("You need to be in a voice channel.").queue();
            return;
        }

        Member self = event.getGuild().getSelfMember();
        GuildVoiceState selfVoiceState = self.getVoiceState();

        if(!selfVoiceState.inAudioChannel())   {
            event.reply("You need to be in the same channel as the bot.").queue();
            return;
        }

        GuildMusicManager guildMusicManager = PlayerManager.get().getGuildMusicManager(event.getGuild());
        TrackScheduler trackScheduler = guildMusicManager.getTrackScheduler();
        trackScheduler.getQueue().clear();
        trackScheduler.getPlayer().stopTrack();

        AudioManager audioManager = event.getGuild().getAudioManager();

        audioManager.closeAudioConnection();

        event.reply("Stopped.").queue();
    }
}
