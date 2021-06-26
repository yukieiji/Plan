/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Plan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Plan. If not, see <https://www.gnu.org/licenses/>.
 */
package com.djrapitops.plan.gathering.listeners.nukkit;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityEndCrystal;
import cn.nukkit.entity.passive.EntityTameable;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDeathEvent;
import cn.nukkit.event.player.PlayerDeathEvent;
import cn.nukkit.item.Item;
import com.djrapitops.plan.delivery.formatting.EntityNameFormatter;
import com.djrapitops.plan.delivery.formatting.ItemNameFormatter;
import com.djrapitops.plan.gathering.cache.SessionCache;
import com.djrapitops.plan.gathering.domain.ActiveSession;
import com.djrapitops.plan.processing.Processing;
import com.djrapitops.plan.processing.processors.player.MobKillProcessor;
import com.djrapitops.plan.processing.processors.player.PlayerKillProcessor;
import com.djrapitops.plan.utilities.logging.ErrorContext;
import com.djrapitops.plan.utilities.logging.ErrorLogger;

import javax.inject.Inject;
import java.util.Optional;

/**
 * Event Listener for detecting player and mob deaths.
 *
 * @author AuroraLS3
 */
public class DeathEventListener implements Listener {

    private final Processing processing;
    private final ErrorLogger errorLogger;

    @Inject
    public DeathEventListener(
            Processing processing,
            ErrorLogger errorLogger
    ) {
        this.processing = processing;
        this.errorLogger = errorLogger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        long time = System.currentTimeMillis();
        Player dead = event.getEntity();
        SessionCache.getCachedSession(dead.getUniqueId()).ifPresent(ActiveSession::addDeath);

        try {
            Optional<Player> foundKiller = findKiller(dead);
            if (!foundKiller.isPresent()) {
                return;
            }
            Player killer = foundKiller.get();

            processing.submitCritical(new PlayerKillProcessor(
                    killer.getUniqueId(), time, dead.getUniqueId(), findWeapon(dead)
            ));
        } catch (Exception e) {
            errorLogger.error(e, ErrorContext.builder().related(event, dead).build());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        Entity dead = event.getEntity();

        try {
            Optional<Player> foundKiller = findKiller(dead);
            if (!foundKiller.isPresent()) {
                return;
            }
            Player killer = foundKiller.get();

            processing.submitCritical(new MobKillProcessor(killer.getUniqueId()));
        } catch (Exception e) {
            errorLogger.error(e, ErrorContext.builder().related(event, dead).build());
        }
    }

    public Optional<Player> findKiller(Entity dead) {
        EntityDamageEvent entityDamageEvent = dead.getLastDamageCause();
        if (!(entityDamageEvent instanceof EntityDamageByEntityEvent)) {
            // Not damaged by entity, can't be a player
            return Optional.empty();
        }

        Entity killer = ((EntityDamageByEntityEvent) entityDamageEvent).getDamager();
        if (killer instanceof Player) return Optional.of((Player) killer);
        if (killer instanceof EntityTameable) return getOwner((EntityTameable) killer);
        if (killer instanceof EntityProjectile) return getShooter((EntityProjectile) killer);
        if (killer instanceof EntityEndCrystal) return findKiller(killer); // Recursive call

        return Optional.empty();
    }

    public String findWeapon(Entity dead) {
        EntityDamageEvent entityDamageEvent = dead.getLastDamageCause();
        Entity killer = ((EntityDamageByEntityEvent) entityDamageEvent).getDamager();
        if (killer instanceof Player) return getItemInHand((Player) killer);
        if (killer instanceof EntityTameable) return getPetType((EntityTameable) killer);

        // EntityProjectile, EntityEndCrystal and all other causes that are not known yet
        return new EntityNameFormatter().apply(killer.getName());
    }

    private String getPetType(EntityTameable tameable) {
        return tameable.getName();
    }

    private String getItemInHand(Player killer) {
        Item itemInHand = killer.getInventory().getItemInHand();
        return new ItemNameFormatter().apply(itemInHand.getName());
    }

    private Optional<Player> getShooter(EntityProjectile projectile) {
        Entity source = projectile.shootingEntity;
        if (source instanceof Player) {
            return Optional.of((Player) source);
        }

        return Optional.empty();
    }

    private Optional<Player> getOwner(EntityTameable tameable) {
        if (!tameable.isTamed()) {
            return Optional.empty();
        }

        Player owner = tameable.getOwner();
        return Optional.of(owner);
    }
}

