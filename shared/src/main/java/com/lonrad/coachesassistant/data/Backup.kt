package com.lonrad.coachesassistant.data

import androidx.annotation.StringRes
import com.lonrad.coachesassistant.shared.R
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Everything a backup holds: every table, for every sport. */
data class BackupData(
    val players: List<PlayerEntity>,
    val games: List<GameEntity>,
    val gameEvents: List<GameEventEntity>,
    val gamePlayers: List<GamePlayerEntity>,
    val cricketScorecards: List<CricketScorecardEntity> = emptyList(),
    val cricketFieldPositions: List<CricketFieldPositionEntity> = emptyList(),
)

/** A backup file that can't be restored, with a message fit to show the user. */
open class BackupFormatException(@StringRes messageRes: Int) : UserFacingException(messageRes)

/** An encrypted backup, which can't be read until the coach gives its password. */
class BackupPasswordRequiredException : BackupFormatException(R.string.backup_password_needed)

/**
 * The backup file format: JSON with each table written field by field, so it stays readable as
 * the database schema changes (a later version reads older files by filling in defaults).
 *
 * A plain backup is [FORMAT_VERSION]. A password-protected backup is [ENCRYPTED_FORMAT_VERSION]: an
 * envelope whose `data` is the encrypted plain backup (see [BackupCrypto]). Older apps don't know
 * that version, so they say it was made by a newer app rather than misreading it. The version goes
 * up only when a change would stop an older app reading new files.
 */
object BackupFormat {
    const val FORMAT_VERSION = 1
    const val ENCRYPTED_FORMAT_VERSION = 2
    private const val APP_TAG = "coaches-assistant"

    /** The backup as text: plain when [password] is null or blank, otherwise encrypted with it. */
    fun encode(data: BackupData, exportedAt: Long, password: String? = null): String {
        val plain = plainJson(data, exportedAt).toString()
        if (password.isNullOrEmpty()) return plain
        val header = JSONObject()
            .put("app", APP_TAG)
            .put("formatVersion", ENCRYPTED_FORMAT_VERSION)
            .put("encrypted", true)
        val aad = associatedData(ENCRYPTED_FORMAT_VERSION, BackupCrypto.ITERATIONS)
        val sealed = BackupCrypto.seal(plain.toByteArray(Charsets.UTF_8), password, aad)
        return header
            .put("iterations", sealed.iterations)
            .put("salt", encodeBase64(sealed.salt))
            .put("iv", encodeBase64(sealed.iv))
            .put("data", encodeBase64(sealed.ciphertext))
            .toString()
    }

    /** Whether [text] is a password-protected backup. Anything that isn't JSON counts as not. */
    fun isEncrypted(text: String): Boolean = try {
        JSONObject(text).optBoolean("encrypted", false)
    } catch (_: JSONException) {
        false
    }

    /**
     * Parses and checks a whole backup; throws [BackupFormatException] if it can't be restored. An
     * encrypted backup needs [password]; without one this throws [BackupPasswordRequiredException].
     */
    fun decode(text: String, password: String? = null): BackupData {
        val root = parseTop(text)
        val version = root.optInt("formatVersion", -1)
        if (version > ENCRYPTED_FORMAT_VERSION) throw BackupFormatException(R.string.backup_from_newer_app)
        if (version < 1) throw BackupFormatException(R.string.backup_damaged)

        if (root.optBoolean("encrypted", false)) {
            if (version != ENCRYPTED_FORMAT_VERSION) throw BackupFormatException(R.string.backup_damaged)
            if (password == null) throw BackupPasswordRequiredException()
            val sealed = try {
                SealedBackup(
                    iterations = root.getInt("iterations"),
                    salt = decodeBase64(root.getString("salt")),
                    iv = decodeBase64(root.getString("iv")),
                    ciphertext = decodeBase64(root.getString("data")),
                )
            } catch (_: JSONException) {
                throw BackupFormatException(R.string.backup_damaged)
            } catch (_: IllegalArgumentException) {
                throw BackupFormatException(R.string.backup_damaged)
            }
            val aad = associatedData(version, sealed.iterations)
            val plain = String(BackupCrypto.open(sealed, password, aad), Charsets.UTF_8)
            return decodePlain(parseTop(plain))
        }
        if (version == ENCRYPTED_FORMAT_VERSION) throw BackupFormatException(R.string.backup_damaged)
        return decodePlain(root)
    }

    private fun plainJson(data: BackupData, exportedAt: Long): JSONObject = JSONObject().apply {
        put("app", APP_TAG)
        put("formatVersion", FORMAT_VERSION)
        put("exportedAt", exportedAt)
        put("players", JSONArray(data.players.map(::playerJson)))
        put("games", JSONArray(data.games.map(::gameJson)))
        put("gameEvents", JSONArray(data.gameEvents.map(::eventJson)))
        put("gamePlayers", JSONArray(data.gamePlayers.map(::gamePlayerJson)))
        put("cricketScorecards", JSONArray(data.cricketScorecards.map(::scorecardJson)))
        put("cricketFieldPositions", JSONArray(data.cricketFieldPositions.map(::fieldPositionJson)))
    }

    private fun parseTop(text: String): JSONObject {
        val root = try {
            JSONObject(text)
        } catch (_: JSONException) {
            throw BackupFormatException(R.string.backup_not_a_backup)
        }
        if (root.optString("app") != APP_TAG) throw BackupFormatException(R.string.backup_not_a_backup)
        return root
    }

    private fun decodePlain(root: JSONObject): BackupData = try {
        BackupData(
            players = root.getJSONArray("players").objects().map(::playerFrom),
            games = root.getJSONArray("games").objects().map(::gameFrom),
            gameEvents = root.getJSONArray("gameEvents").objects().map(::eventFrom),
            gamePlayers = root.getJSONArray("gamePlayers").objects().map(::gamePlayerFrom),
            // Added with cricket; backups from before then have none.
            cricketScorecards = root.optJSONArray("cricketScorecards")?.objects()?.map(::scorecardFrom).orEmpty(),
            cricketFieldPositions = root.optJSONArray("cricketFieldPositions")?.objects()?.map(::fieldPositionFrom).orEmpty(),
        )
    } catch (_: JSONException) {
        throw BackupFormatException(R.string.backup_damaged)
    }

    /** The header fields an encrypted backup's ciphertext is bound to, so changing them is detected. */
    private fun associatedData(version: Int, iterations: Int): ByteArray =
        "$APP_TAG/$version/$iterations".toByteArray(Charsets.UTF_8)

    private fun encodeBase64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)
    private fun decodeBase64(text: String): ByteArray = java.util.Base64.getDecoder().decode(text)

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.putNullable(key: String, value: Any?) = put(key, value ?: JSONObject.NULL)
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

    private fun playerJson(p: PlayerEntity) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        putNullable("jerseyNumber", p.jerseyNumber)
        put("sport", p.sport)
        putNullable("position", p.position)
    }

    private fun playerFrom(o: JSONObject) = PlayerEntity(
        id = o.getLong("id"),
        name = o.getString("name"),
        jerseyNumber = o.intOrNull("jerseyNumber"),
        sport = o.getString("sport"),
        position = o.stringOrNull("position"),
    )

    private fun gameJson(g: GameEntity) = JSONObject().apply {
        put("id", g.id)
        put("title", g.title)
        put("opponent", g.opponent)
        put("createdAt", g.createdAt)
        put("teamScore", g.teamScore)
        put("opponentScore", g.opponentScore)
        put("mvpVotesJson", g.mvpVotesJson)
        put("runningTimeMillis", g.runningTimeMillis)
        putNullable("timerLastStartedAt", g.timerLastStartedAt)
        put("isFinished", g.isFinished)
        putNullable("finishedAt", g.finishedAt)
        put("currentPeriod", g.currentPeriod)
        put("periodDurationMillis", g.periodDurationMillis)
        put("notes", g.notes)
        put("sport", g.sport)
        put("onCourtSize", g.onCourtSize)
        put("periodCount", g.periodCount)
        put("extras", g.extras)
        put("teamWickets", g.teamWickets)
        put("opponentWickets", g.opponentWickets)
    }

    private fun gameFrom(o: JSONObject) = GameEntity(
        id = o.getLong("id"),
        title = o.getString("title"),
        opponent = o.getString("opponent"),
        createdAt = o.getLong("createdAt"),
        teamScore = o.getInt("teamScore"),
        opponentScore = o.getInt("opponentScore"),
        mvpVotesJson = o.getString("mvpVotesJson"),
        runningTimeMillis = o.getLong("runningTimeMillis"),
        timerLastStartedAt = o.longOrNull("timerLastStartedAt"),
        isFinished = o.getBoolean("isFinished"),
        finishedAt = o.longOrNull("finishedAt"),
        currentPeriod = o.getInt("currentPeriod"),
        periodDurationMillis = o.getLong("periodDurationMillis"),
        notes = o.getString("notes"),
        sport = o.getString("sport"),
        onCourtSize = o.getInt("onCourtSize"),
        periodCount = o.getInt("periodCount"),
        extras = o.optInt("extras", 0),
        teamWickets = o.optInt("teamWickets", 0),
        opponentWickets = o.optInt("opponentWickets", 0),
    )

    private fun eventJson(e: GameEventEntity) = JSONObject().apply {
        put("id", e.id)
        put("gameId", e.gameId)
        put("timestamp", e.timestamp)
        put("type", e.type)
        putNullable("playerId", e.playerId)
        putNullable("relatedPlayerId", e.relatedPlayerId)
        put("points", e.points)
        put("note", e.note)
        put("gameTimeMillis", e.gameTimeMillis)
    }

    private fun eventFrom(o: JSONObject) = GameEventEntity(
        id = o.getLong("id"),
        gameId = o.getLong("gameId"),
        timestamp = o.getLong("timestamp"),
        type = o.getString("type"),
        playerId = o.longOrNull("playerId"),
        relatedPlayerId = o.longOrNull("relatedPlayerId"),
        points = o.getInt("points"),
        note = o.getString("note"),
        gameTimeMillis = o.getLong("gameTimeMillis"),
    )

    private fun gamePlayerJson(gp: GamePlayerEntity) = JSONObject().apply {
        put("gameId", gp.gameId)
        put("playerId", gp.playerId)
        put("onCourt", gp.onCourt)
        put("absent", gp.absent)
    }

    private fun gamePlayerFrom(o: JSONObject) = GamePlayerEntity(
        gameId = o.getLong("gameId"),
        playerId = o.getLong("playerId"),
        onCourt = o.getBoolean("onCourt"),
        absent = o.getBoolean("absent"),
    )

    private fun scorecardJson(c: CricketScorecardEntity) = JSONObject().apply {
        put("id", c.id)
        put("gameId", c.gameId)
        putNullable("playerId", c.playerId)
        put("dismissal", c.dismissal)
        put("runs", c.runs)
        put("ballsFaced", c.ballsFaced)
        put("fours", c.fours)
        put("sixes", c.sixes)
        put("ballsBowled", c.ballsBowled)
        put("maidens", c.maidens)
        put("runsConceded", c.runsConceded)
        put("wickets", c.wickets)
        put("catches", c.catches)
        put("runOuts", c.runOuts)
        put("stumpings", c.stumpings)
    }

    private fun scorecardFrom(o: JSONObject) = CricketScorecardEntity(
        id = o.getLong("id"),
        gameId = o.getLong("gameId"),
        playerId = o.longOrNull("playerId"),
        dismissal = o.getString("dismissal"),
        runs = o.getInt("runs"),
        ballsFaced = o.getInt("ballsFaced"),
        fours = o.getInt("fours"),
        sixes = o.getInt("sixes"),
        ballsBowled = o.getInt("ballsBowled"),
        maidens = o.getInt("maidens"),
        runsConceded = o.getInt("runsConceded"),
        wickets = o.getInt("wickets"),
        catches = o.getInt("catches"),
        runOuts = o.getInt("runOuts"),
        stumpings = o.getInt("stumpings"),
    )

    private fun fieldPositionJson(f: CricketFieldPositionEntity) = JSONObject().apply {
        put("gameId", f.gameId)
        put("playerId", f.playerId)
        put("position", f.position)
    }

    private fun fieldPositionFrom(o: JSONObject) = CricketFieldPositionEntity(
        gameId = o.getLong("gameId"),
        playerId = o.getLong("playerId"),
        position = o.getString("position"),
    )
}

/**
 * [games] with any running clock stopped at [now]: the elapsed time is kept and the clock is left
 * stopped, so a restored game doesn't look as if its clock ran the whole time since the backup.
 */
fun stopRunningClocks(games: List<GameEntity>, now: Long): List<GameEntity> = games.map { game ->
    if (game.timerLastStartedAt == null) {
        game
    } else {
        game.copy(runningTimeMillis = game.currentGameTimeMillis(now), timerLastStartedAt = null)
    }
}
