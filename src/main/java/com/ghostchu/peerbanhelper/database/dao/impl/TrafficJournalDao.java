package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.TrafficJournalEntity;
import com.ghostchu.peerbanhelper.lab.Laboratory;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Component
public final class TrafficJournalDao extends AbstractPBHDao<TrafficJournalEntity, Long> {
    private final Laboratory laboratory;

    public TrafficJournalDao(@Autowired Database database, @Autowired Laboratory laboratory) throws SQLException {
        super(database.getDataSource(), TrafficJournalEntity.class);
        this.laboratory = laboratory;
    }

    public TrafficDataDto getTodayData(String downloader) throws Exception {
        Timestamp startOfToday = new Timestamp(MiscUtil.getStartOfToday(System.currentTimeMillis()));
        List<TrafficDataDto> results;
        if (downloader == null || downloader.isBlank()) {
            results = getAllDownloadersOverallData(startOfToday, startOfToday).stream().toList();
        } else {
            results = getSpecificDownloaderOverallData(downloader, startOfToday, startOfToday).stream().toList();
        }
        if (results.isEmpty()) {
            return new TrafficDataDto(startOfToday, 0, 0);
        } else {
            return new TrafficDataDto(startOfToday, results.getFirst().getDataOverallUploaded(), results.getFirst().getDataOverallDownloaded());
        }
    }

    public TrafficJournalEntity updateData(String downloader, long overallDownloaded, long overallUploaded, long overallDownloadedProtocol, long overallUploadedProtocol) throws SQLException {
        long timestamp = MiscUtil.getStartOfToday(System.currentTimeMillis());
        TrafficJournalEntity journalEntity = queryBuilder()
                .where()
                .eq("downloader", downloader)
                .and()
                .eq("timestamp", timestamp)
                .queryForFirst();
        if (journalEntity == null) {
            journalEntity = new TrafficJournalEntity();
            journalEntity.setDownloader(downloader);
            journalEntity.setTimestamp(timestamp);
            journalEntity.setDataOverallDownloadedAtStart(overallDownloaded);
            journalEntity.setDataOverallUploadedAtStart(overallUploaded);
            journalEntity.setProtocolOverallDownloadedAtStart(overallDownloadedProtocol);
            journalEntity.setProtocolOverallUploadedAtStart(overallUploadedProtocol);
        }
        journalEntity.setDataOverallDownloaded(overallDownloaded);
        journalEntity.setDataOverallUploaded(overallUploaded);
        journalEntity.setProtocolOverallDownloaded(overallDownloadedProtocol);
        journalEntity.setProtocolOverallUploaded(overallUploadedProtocol);
        createOrUpdate(journalEntity);
        return journalEntity;
    }

    public List<TrafficDataDto> getDayOffsetData(String downloader, Timestamp startAt, Timestamp endAt) throws Exception {
        List<TrafficDataDto> results;
        if (downloader == null || downloader.isBlank()) {
            results = getAllDownloadersOverallData(startAt, endAt).stream().toList();
        } else {
            results = getSpecificDownloaderOverallData(downloader, startAt, endAt).stream().toList();
        }
        return results;
    }

    public List<TrafficDataDto> getAllDownloadersOverallData(Timestamp start, Timestamp end) throws Exception {
        try (var results = queryBuilder().selectRaw(
                        "timestamp",
                        "SUM(dataOverallUploadedAtStart) AS totalUploadedAtStart",
                        "SUM(dataOverallUploaded) AS totalUploaded",
                        "SUM(dataOverallDownloadedAtStart) AS totalDownloadedAtStart",
                        "SUM(dataOverallDownloaded) AS totalDownloaded"
                )
                .where()
                .ge("timestamp", start.getTime())
                .and()
                .le("timestamp", end.getTime())
                .queryBuilder()
                .groupBy("timestamp")
                .queryRaw()) {
            return results.getResults().stream().map(args ->
                    new TrafficData(
                            new Timestamp(Long.parseLong(args[0])),
                            Long.parseLong(args[1]),
                            Long.parseLong(args[2]),
                            Long.parseLong(args[3]),
                            Long.parseLong(args[4])
                    )
            ).map(data -> new TrafficDataDto(data.getTimestamp(),
                    data.getDataOverallUploaded() - data.getDataOverallUploadedAtStart(),
                    data.getDataOverallDownloaded() - data.getDataOverallDownloadedAtStart())).toList();
        }
    }

    public List<TrafficDataDto> getSpecificDownloaderOverallData(String downloadName, Timestamp start, Timestamp end) throws Exception {
        return queryBuilder().orderBy("timestamp", true)
                .where()
                .eq("downloader", downloadName)
                .and()
                .ge("timestamp", start.getTime())
                .and()
                .le("timestamp", end.getTime())
                .queryBuilder()
                .query().stream().map(e -> new TrafficData(
                        new Timestamp(e.getTimestamp()),
                        e.getDataOverallUploadedAtStart(),
                        e.getDataOverallUploaded(),
                        e.getDataOverallDownloadedAtStart(),
                        e.getDataOverallDownloaded()))
                .map(data -> new TrafficDataDto(data.getTimestamp(),
                        data.getDataOverallUploaded() - data.getDataOverallUploadedAtStart(),
                        data.getDataOverallDownloaded() - data.getDataOverallDownloadedAtStart()))
                .toList();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficDataDto {
        private Timestamp timestamp;
        private long dataOverallUploaded;
        private long dataOverallDownloaded;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficData {
        private Timestamp timestamp;
        private long dataOverallUploadedAtStart;
        private long dataOverallUploaded;
        private long dataOverallDownloadedAtStart;
        private long dataOverallDownloaded;
    }

}
