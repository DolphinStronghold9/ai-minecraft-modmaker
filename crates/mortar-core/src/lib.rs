//! Block changes, ghosts, batching and the undo journal.
//!
//! A turn starts as a [`Ghost`]: the blocks Mortar wants to place, relative to an
//! origin. The player can move or rotate it. On yes, it becomes a list of
//! [`BlockChange`]s, each one remembering the block it replaces, so the turn can
//! be undone exactly.

use std::collections::BTreeMap;

/// Default value of the `max_block_modifications` game rule.
pub const MAX_BLOCK_MODIFICATIONS: usize = 32_768;

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct Pos {
    pub x: i32,
    pub y: i32,
    pub z: i32,
}

impl Pos {
    pub const fn new(x: i32, y: i32, z: i32) -> Self {
        Self { x, y, z }
    }
}

/// A block id with optional state, like `minecraft:ladder[facing=south]`.
pub type Block = String;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BlockChange {
    pub pos: Pos,
    pub before: Block,
    pub after: Block,
}

/// A build that hasn't been placed yet. Positions are relative to `origin`.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Ghost {
    pub origin: Pos,
    pub blocks: BTreeMap<Pos, Block>,
}

impl Ghost {
    pub fn len(&self) -> usize {
        self.blocks.len()
    }

    pub fn is_empty(&self) -> bool {
        self.blocks.is_empty()
    }

    /// `/move dx dy dz`
    pub fn moved(mut self, dx: i32, dy: i32, dz: i32) -> Self {
        self.origin = Pos::new(self.origin.x + dx, self.origin.y + dy, self.origin.z + dz);
        self
    }

    /// `/rotate 90`: quarter turns clockwise seen from above, around the origin.
    /// Block states that face a direction are turned by the companion when placing.
    pub fn rotated(self, quarter_turns: i32) -> Self {
        let turns = quarter_turns.rem_euclid(4);
        let blocks = self
            .blocks
            .into_iter()
            .map(|(p, b)| {
                let (mut x, mut z) = (p.x, p.z);
                for _ in 0..turns {
                    (x, z) = (-z, x);
                }
                (Pos::new(x, p.y, z), b)
            })
            .collect();
        Self {
            origin: self.origin,
            blocks,
        }
    }

    /// Bill of materials: block id without state, and how many.
    pub fn materials(&self) -> BTreeMap<String, usize> {
        let mut m = BTreeMap::new();
        for b in self.blocks.values() {
            let id = b.split('[').next().unwrap_or(b).to_string();
            *m.entry(id).or_insert(0) += 1;
        }
        m
    }

    /// Turn the ghost into changes, reading what's in the world now via `world`.
    pub fn place(&self, mut world: impl FnMut(Pos) -> Block) -> Vec<BlockChange> {
        self.blocks
            .iter()
            .map(|(p, b)| {
                let pos = Pos::new(
                    self.origin.x + p.x,
                    self.origin.y + p.y,
                    self.origin.z + p.z,
                );
                BlockChange {
                    pos,
                    before: world(pos),
                    after: b.clone(),
                }
            })
            .filter(|c| c.before != c.after)
            .collect()
    }
}

/// Split changes into batches that each stay under the block-change limit,
/// bottom layer first so nothing is placed in mid-air.
pub fn batches(mut changes: Vec<BlockChange>, limit: usize) -> Vec<Vec<BlockChange>> {
    changes.sort_by_key(|c| (c.pos.y, c.pos.x, c.pos.z));
    changes
        .chunks(limit.max(1))
        .map(<[BlockChange]>::to_vec)
        .collect()
}

/// The changes that undo a turn: same positions, `before` and `after` swapped, newest first.
pub fn undo(turn: &[BlockChange]) -> Vec<BlockChange> {
    turn.iter()
        .rev()
        .map(|c| BlockChange {
            pos: c.pos,
            before: c.after.clone(),
            after: c.before.clone(),
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tower() -> Ghost {
        let mut g = Ghost {
            origin: Pos::new(212, 71, -340),
            ..Default::default()
        };
        for y in 0..3 {
            g.blocks
                .insert(Pos::new(1, y, 0), "minecraft:stone_bricks".into());
        }
        g.blocks
            .insert(Pos::new(0, 3, 0), "minecraft:lantern[hanging=false]".into());
        g
    }

    #[test]
    fn four_quarter_turns_change_nothing() {
        assert_eq!(tower().rotated(4), tower());
        assert_ne!(tower().rotated(1), tower());
    }

    #[test]
    fn undo_restores_the_world() {
        let changes = tower().place(|_| "minecraft:air".to_string());
        assert_eq!(changes.len(), 4);
        let back = undo(&changes);
        assert!(back.iter().all(|c| c.after == "minecraft:air"));
    }

    #[test]
    fn batches_respect_the_limit_and_go_bottom_up() {
        let changes = tower().place(|_| "minecraft:air".to_string());
        let parts = batches(changes, 3);
        assert_eq!(parts.iter().map(Vec::len).collect::<Vec<_>>(), vec![3, 1]);
        assert_eq!(parts[0][0].pos.y, 71);
    }

    #[test]
    fn materials_ignore_block_state() {
        let m = tower().materials();
        assert_eq!(m["minecraft:stone_bricks"], 3);
        assert_eq!(m["minecraft:lantern"], 1);
    }
}
