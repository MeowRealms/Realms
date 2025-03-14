package ink.ptms.realms.data

import ink.ptms.realms.util.toAABB
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Particle.DustOptions
import org.bukkit.util.Vector
import taboolib.expansion.Id
import taboolib.expansion.Key
import taboolib.expansion.Length
import taboolib.module.effect.ParticleSpawner
import taboolib.module.effect.shape.Cube
import taboolib.module.effect.shape.Line
import taboolib.module.navigation.BoundingBox
import taboolib.module.nms.createPacket
import taboolib.module.nms.sendBundlePacket
import taboolib.platform.util.onlinePlayers
import taboolib.platform.util.toBukkitLocation
import taboolib.platform.util.toProxyLocation
import java.util.*

/**
 * Realms
 * ink.ptms.realms.data.RealmBlock
 *
 * @author sky
 * @since 2021/3/11 5:09 下午
 */
class RealmBlock(
    @Id
    val center: Location,
    @Key
    @Length(32)
    val serverName: String,
    val owner: UUID,
    val size: Int,
    @Length(32)
    var name: String,
    @Length(64)
    var joinMessage: String = "§e+ §f$name | 欢迎",
    @Length(64)
    var leaveMessage: String = "§e- §f$name | 慢走",
    var permissions: MutableMap<String, Boolean> = mutableMapOf(),
    var users: MutableMap<String, MutableMap<String, Boolean>> = mutableMapOf(),
    var extend: MutableMap<Position, Int> = mutableMapOf(),
    var teleportLocation: Location = center.clone().add(0.0, 1.0, 0.0)
) {

    val aabb = ArrayList<BoundingBox>()

    val node: String
        get() = "realm_${center.blockX}_${center.blockY}_${center.blockZ}"

    init {
        update()
    }

    /**
     * 权限检查
     */
    fun hasPermission(key: String, player: String? = null, def: Boolean = false): Boolean {
        return if (player != null && users.containsKey(player)) {
            users[player]!![key] ?: permissions[key] ?: def
        } else {
            permissions[key] ?: def
        }
    }

    /**
     * 缓存中心及扩展的碰撞箱
     */
    fun update() {
        aabb.clear()
        aabb.add(center.toCenterLocation().toAABB(size))
        aabb.addAll(extend.map { it.key.toCenter().toBukkitLocation().toAABB(it.value) })
    }

    /**
     * 是否在领域内
     */
    fun inside(loc: Location): Boolean {
        return aabb.any { it.contains(loc.x, loc.y, loc.z) }
    }

    /**
     * 判断是否碰撞
     * 就是各轴互相是否包含，(other 包含当前包围盒) || (当前的包围盒包含 other)
     */
    fun intersect(other: BoundingBox): Boolean {
        return aabb.any {
            return@any ((it.minX >= other.minX && it.minX <= other.maxX) || (other.minX >= it.minX && other.minX <= it.maxX))
                    && ((it.minY >= other.minY && it.minY <= other.maxY) || (other.minY >= it.minY && other.minY <= it.maxY))
                    && ((it.minZ >= other.minZ && it.minZ <= other.maxZ) || (other.minZ >= it.minZ && other.minZ <= it.maxZ))
        }
    }

    /**
     * 展示领地边界和子领域连接
     */
    fun particleDisplay() {
        val packets = mutableListOf<Any>()
        aabb.forEach { box ->
            Cube(
                Location(center.world, box.minX, box.minY, box.minZ).toProxyLocation(),
                Location(center.world, box.maxX, box.maxY, box.maxZ).toProxyLocation(),
                1.0,
                object : ParticleSpawner {
                    override fun spawn(location: taboolib.common.util.Location) {
                        packets += Particle.END_ROD.createPacket(
                            location.toBukkitLocation(),
                            offset = Vector(0, 0, 0),
                            count = 1
                        )
                    }
            }).show()
        }
        extend.forEach { (location, _) ->
            Line(
                center.toCenterLocation().toProxyLocation(),
                location.toCenter().toProxyLocation(),
                0.5,
                object : ParticleSpawner {
                    override fun spawn(location: taboolib.common.util.Location) {
                        packets += Particle.REDSTONE.createPacket(
                            location.toBukkitLocation(),
                            offset = Vector(0, 0, 0),
                            count = 5,
                            data = DustOptions(Color.fromRGB(152, 249, 255), 1f)
                        )
                    }
                }
            ).show()
        }
        onlinePlayers.filter { it.world == center.world && it.location.distance(center) <= 128 }.forEach {
            it.sendBundlePacket(packets)
        }
    }

    private fun BoundingBox.containsIn(x: Double, y: Double, z: Double): Boolean {
        return x - 1 > minX && x + 1 < maxX && y - 1 > minY && y + 1 < maxY && z - 1 > minZ && z + 1 < maxZ
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RealmBlock) return false
        if (node != other.node) return false
        return true
    }

    override fun hashCode(): Int {
        return node.hashCode()
    }

    override fun toString(): String {
        return "RealmBlock(owner=$owner, size=$size, name=$name, permissions=$permissions, users=$users, extends=$extend, node='$node')"
    }
}