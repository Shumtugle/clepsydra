# Clepsydra

A small game for Android. A round glass flask with a maze inside it. Water
falls from above; it can get in through the neck. Turn the flask with a
finger and lead the water through the rings to the opening at the bottom.
What counts is a single pour: every drop that leaves the bottom without a
pause longer than a third of a second.

Water that gets in through the bottom opening turns grey. It flows like the
rest, takes up room in the cups of the maze, and never counts.

Every new flask is a new maze, always passable from the neck to the bottom,
and never handed over untried: the game plays each flask once out of sight,
by the flask's own rules, and keeps only those it pours water out of. The
next two are made and tried ahead while one is played.
There are five tiers, from gentle (two rings, wide gaps, a short way down) to
knotty (four rings, narrow gaps, a long way), and three styles: scattered
gaps, a spiral of gaps, and chambers walled off beside the gaps, which hold
water like cups. The flasks do not climb a ladder: every five of them hold
each tier once, shuffled, never two hard ones side by side. Five beads at
the top tell how knotty the flask in hand is.

A flask is over when its water has fallen and nothing has poured for a
while; even then it can still be turned and emptied. A flask poured poorly
or not at all is then played once more by the game itself, from the start
and at twice the pace, along the way its maker found through it; a tap skips
that, and a new flask follows.

Some flasks hold the hand back. One kind turns only so far, another only for
so long; then it sets fast. The allowance is never mean: the generator works
out the least turning that leads water from the neck to the bottom, and the
flask allows a third more, and a little over a quarter turn besides.

Some flasks hold bubbles of air, one round bubble each: across a corridor,
corking a gap, or corking a vent in the flask's own glass. The flask's one
decision is a single stroke: every bubble the finger passes over pops, and
the rest stay to the end. A bubble on the way down has to go; one in the
glass holds the water in, and popped, lets it run out and away; the others
are there to be told apart. Some walls hold seeds, pearls stuck to the glass: water runs
past them, but a pearl wetted enough opens into a flower across the
corridor and shuts it, a red one into a rose, a green one into clover, a
golden one into a daisy. A flower closes a corridor, never the whole flask:
the way down always stays open around it.

Some bands hold a float: a red and white bob on a rail between a gap in the
ring above and a gap in the ring below. Water about it lifts it into the
upper gap and shuts it; when the water drains away, it sinks into the lower
gap and shuts that. A float meters water: it fills, lets go, and fills again.

Rarer troubles: a flask that turns by itself, which a finger holds like a
rubber band and which slips loose when pulled too hard; a flask that flips
over like a coin now and then, so left and right change places; and weather
among the rain. Snow freezes the water it meets, and the ice sticks to the
glass until it melts. Lava boils water into steam, which rises and leaves.
Slime, only where time is short, makes water thick and slow. No flask
carries more than two troubles at once.

A plain flask, with none of these troubles, sometimes breathes instead: once
its rain is over, the glass swells a little now and then, its walls fade,
and they come back as another maze of the same knottiness. The flask wall,
the neck and the bottom stay where they are.

All the keys sit in the top row: sound, tone and language on the left; on
the right Demo, which plays the flask for you from the start whenever asked, a
page about the game, and a new flask at any time. A flask is never taken
out of the player's hands: when it runs dry or rests, a card offers a new
flask or the way through, and the glass still turns. The page is
inside the game and needs no network: how it plays, every trouble with its
picture, and how the flasks are made fair, in the language the game speaks. Below them, the best pour of this
flask, the beads of its knottiness, and the record.

Three tones, each a stuff for the maze and a room for it to stand in: glass,
brass and stone, each with a day and a night face that follows the phone.
Water, bubbles and flowers look the same in every tone. The music is written as it plays, a new piece of plucked
drops each time the last one ends, so it never repeats; or silence.

## Languages

The game speaks English, Finnish and Russian. On first start it speaks the
phone's language if it has it, and English otherwise; the language key in
the top row changes it at any time, and shows the code of the language
spoken: `en`, `fi`, `ru`.

English is written in the code. Finnish and Russian are plain text modules,
kept in `modules/` and carried inside the game as resources. Any other
language can be loaded from a file with the same key. The same menu saves
the language spoken now as a file: in English it is a blank template, in
Finnish or Russian a filled one to correct or to start from. Fill it in with
any text editor and load it back; `modules/template.txt` is the same blank
template. A module opens with its head line,
names itself (`module Suomi`), gives its code (`code fi`), and then each key
and its words, separated by a tab, with the English above as a comment.
Numbers go where the English has `{n}` and `{m}`.

## The page

`docs/` holds the game's page, with every feature on it.

## Inside

- `Maze` cuts the flask into bands and sectors, joins them through the gaps,
  and keeps only mazes with a way from the neck to the bottom.
- `Water` is a few hundred round drops. The drops live on the screen and the
  glass turns under them; a moving wall lends a drop its own speed.
- `Course` decides which flask comes next, and tries it before dealing it.
- `Hand` knows the way through a flask: it tries every flask, weighs what each bubble does, and plays a flask from the start to show the way.
- `Music` plays what the composer writes, and steps aside when the phone needs the sound.
- `Composer` writes a new piece of music, as a MIDI file, each time it is asked.
- `Words` holds the English and takes in a language module.
- `Board` draws everything on one view and turns the flask under the finger.

No permissions, no network, no libraries. The best pour is kept on the phone.

## Install

Download the APK from the latest release and open it on the phone; Android
asks once to allow installing from that source. Android 8.0 or newer.

## Building

Plain AOSP tools, no Gradle: `aapt`, `javac` (target 8), `dalvik-exchange`,
`zipalign`, `apksigner`, and `android.jar` of API 33.

```sh
KEYSTORE=path/to/keystore KSPASS=... SDK=path/to/android.jar sh build.sh clepsydra-1.00
```

Releases are described step by step in `RELEASING.md`, changes in
`CHANGELOG.md`.

## License

MIT
