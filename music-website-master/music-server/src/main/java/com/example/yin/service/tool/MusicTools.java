package com.example.yin.service.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.yin.mapper.SongMapper;
import com.example.yin.mapper.SingerMapper;
import com.example.yin.mapper.SongListMapper;
import com.example.yin.model.domain.Song;
import com.example.yin.model.domain.Singer;
import com.example.yin.model.domain.SongList;
import com.example.yin.model.request.PlayAction;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class MusicTools {

    @Autowired
    private SongMapper songMapper;

    @Autowired
    private SingerMapper singerMapper;

    @Autowired
    private SongListMapper songListMapper;

    // 旁路传递播放指令，避免 AI 看到 [PLAY:] 标记后困惑
    private final ThreadLocal<List<PlayAction>> pendingActions =
            ThreadLocal.withInitial(ArrayList::new);

    /** 取出本轮对话积累的所有播放指令，并清空 */
    public List<PlayAction> drainActions() {
        List<PlayAction> actions = new ArrayList<>(pendingActions.get());
        pendingActions.get().clear();
        return actions;
    }

    /**
     * @deprecated 保留旧方法以兼容，新代码请使用 drainActions() 获取结构化数据
     */
    @Deprecated
    public List<String> drainCommands() {
        List<String> cmds = new ArrayList<>();
        for (PlayAction a : pendingActions.get()) {
            cmds.add(toLegacyMarker(a));
        }
        pendingActions.get().clear();
        return cmds;
    }

    private String toLegacyMarker(PlayAction a) {
        switch (a.getType()) {
            case "play": return "[PLAY:" + a.getSongId() + "]";
            case "pause": return "[PAUSE]";
            case "resume": return "[RESUME]";
            case "next": return "[NEXT]";
            case "volume": return "[VOLUME:" + a.getVolume() + "]";
            default: return "";
        }
    }

    private PlayAction buildAction(String type) {
        PlayAction a = new PlayAction();
        a.setType(type);
        return a;
    }

    // ==================== 歌曲搜索 ====================

    @Tool("搜索歌曲（仅搜索，不播放）：根据歌名关键词在数据库中搜索匹配的歌曲。用户想搜索/查找歌曲时用，想播放时用 playSong。")
            public List<Map<String, Object>> searchSongs(String keyword) {
        List<Song> songs = songMapper.selectList(
                new QueryWrapper<Song>().like("name",
                        keyword).last("LIMIT 20"));
        return formatSongResults(songs);
    }

    @Tool("根据歌手名搜索：搜索指定歌手的所有歌曲。")
    public List<Map<String, Object>> searchSongsBySinger(String
                                                                 singerName) {
        List<Singer> singers = singerMapper.selectList(
                new QueryWrapper<Singer>().like("name", singerName));
        if (singers.isEmpty()) return Collections.emptyList();

        List<Integer> singerIds = singers.stream()
                .map(Singer::getId).collect(Collectors.toList());
        List<Song> songs = songMapper.selectList(
                new QueryWrapper<Song>().in("singer_id",
                        singerIds).last("LIMIT 30"));

        Map<Integer, String> nameMap = singers.stream()
                .collect(Collectors.toMap(Singer::getId,
                        Singer::getName));
        for (Song s : songs) {
            s.setSingerName(nameMap.get(s.getSingerId()));
        }
        return formatSongResults(songs);
    }

    // ==================== 排行榜 ====================

    @Tool("获取排行榜：type 可选 day(日榜)、week(周榜)、month(月榜)，默认 day")
            public List<Map<String, Object>> getRankList(String type) {
        List<Song> songs;
        switch (type != null ? type.toLowerCase() : "day") {
            case "week":  songs = songMapper.selectRankByWeek();
                break;
            case "month": songs = songMapper.selectRankByMonth();
                break;
            default:      songs = songMapper.selectRankByDay();
                break;
        }
        return formatSongResults(songs);
    }

    // ==================== 歌单 ====================

    @Tool("获取所有歌单列表，返回歌单名称、风格和ID")
    public List<Map<String, Object>> getSongLists() {
        List<SongList> lists = songListMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SongList sl : lists) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", sl.getId());
            item.put("title", sl.getTitle());
            item.put("style", sl.getStyle());
            item.put("introduction", sl.getIntroduction());
            result.add(item);
        }
        return result;
    }

    @Tool("搜索歌单（仅搜索歌单列表，不播放歌曲）：根据风格关键词搜索歌单，如'伤感'、'摇滚'、'古风'")
            public List<Map<String, Object>> searchSongListByStyle(String
            style) {
        List<SongList> lists = songListMapper.selectList(
                new QueryWrapper<SongList>().like("style", style));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SongList sl : lists) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", sl.getId());
            item.put("title", sl.getTitle());
            item.put("style", sl.getStyle());
            item.put("introduction", sl.getIntroduction());
            result.add(item);
        }
        return result;
    }

    // ==================== 歌手 ====================

    @Tool("搜索歌手信息：根据歌手名搜索，返回歌手基本信息")
    public List<Map<String, Object>> searchSingers(String keyword) {
        List<Singer> singers = singerMapper.selectList(
                new QueryWrapper<Singer>().like("name",
                        keyword).last("LIMIT 10"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Singer s : singers) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", s.getId());
            item.put("name", s.getName());
            item.put("sex", s.getSex() != null && s.getSex() == 1 ?
                    "男" : "女");
            item.put("location", s.getLocation());
            item.put("introduction", s.getIntroduction());
            result.add(item);
        }
        return result;
    }

    // ==================== 播放控制 ====================

    @Tool("播放歌曲：用户说'播放XX'时调用此工具。自动搜索并播放，无需先调用搜索工具。songName 为必填的歌名。")
    public String playSong(String songName, String singerName) {
        QueryWrapper<Song> qw = new QueryWrapper<Song>()
                .like("name", songName);
        if (singerName != null && !singerName.isBlank()) {
            List<Singer> singers = singerMapper.selectList(
                    new QueryWrapper<Singer>().like("name", singerName));
            if (!singers.isEmpty()) {
                List<Integer> ids = singers.stream()
                        .map(Singer::getId).collect(Collectors.toList());
                qw.in("singer_id", ids);
            }
        }
        Song song = songMapper.selectOne(qw.last("LIMIT 1"));
        if (song == null) {
            return "未找到歌曲「" + songName + "」，请用 searchSongs 帮用户搜索。";
        }
        String singer = song.getSingerName();
        if (singer == null || singer.isBlank()) {
            Singer s = singerMapper.selectById(song.getSingerId());
            singer = s != null ? s.getName() : "未知";
        }

        // 旁路：存储结构化播放指令，前端可直接使用完整歌曲信息
        PlayAction action = buildAction("play");
        action.setSongId(song.getId());
        action.setName(song.getName());
        action.setSingerName(singer);
        action.setUrl(song.getUrl());
        action.setPic(song.getPic());
        action.setLyric(song.getLyric());
        pendingActions.get().add(action);

        return "已找到「" + song.getName() + "」- " + singer
                + "，系统已自动为用户播放，你只需要告知用户正在播放即可。";
    }

    @Tool("暂停播放")
    public String pausePlayback() {
        pendingActions.get().add(buildAction("pause"));
        return "已暂停播放，告知用户即可。";
    }

    @Tool("继续播放")
    public String resumePlayback() {
        pendingActions.get().add(buildAction("resume"));
        return "已继续播放，告知用户即可。";
    }

    @Tool("下一首")
    public String nextSong() {
        pendingActions.get().add(buildAction("next"));
        return "已切换到下一首，告知用户即可。";
    }

    @Tool("设置音量：volume 取值范围 0-100")
    public String setVolume(int volume) {
        int v = Math.max(0, Math.min(100, volume));
        PlayAction action = buildAction("volume");
        action.setVolume(v);
        pendingActions.get().add(action);
        return "音量已设为 " + v + "，告知用户即可。";
    }

    // ==================== 推荐 ====================

    @Tool("推荐歌曲：根据用户的心情或场景推荐热门歌曲，mood 如'开心'、'伤感'、'放松'、'励志'")
            public List<Map<String, Object>> recommendSongs(String mood) {
        List<Song> songs = songMapper.selectList(
                new
                        QueryWrapper<Song>().orderByDesc("play_count").last("LIMIT 20"));
        return formatSongResults(songs);
    }

    // ==================== 内部方法 ====================

    private List<Map<String, Object>> formatSongResults(List<Song>
                                                                songs) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (songs == null || songs.isEmpty()) return result;

        Set<Integer> singerIds = songs.stream()
                .map(Song::getSingerId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, String> nameMap = new HashMap<>();
        if (!singerIds.isEmpty()) {
            singerMapper.selectBatchIds(singerIds)
                    .forEach(s -> nameMap.put(s.getId(), s.getName()));
        }

        for (Song song : songs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", song.getId());
            item.put("name", song.getName());
            item.put("singerName",
                    nameMap.getOrDefault(song.getSingerId(), "未知"));
            item.put("introduction", song.getIntroduction());
            item.put("url", song.getUrl());
            item.put("pic", song.getPic());
            result.add(item);
        }
        return result;
    }
}