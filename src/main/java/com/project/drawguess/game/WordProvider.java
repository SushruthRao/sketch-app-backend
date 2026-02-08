package com.project.drawguess.game;

import java.util.Random;

public class WordProvider {
	private static final Random random = new Random();

	private static final String[] WORDS = {
		"apple", "banana", "castle", "dragon", "elephant",
		"flower", "guitar", "hammer", "island", "jungle",
		"kite", "ladder", "monkey", "notebook", "octopus",
		"penguin", "queen", "rainbow", "sandwich", "tornado",
		"umbrella", "volcano", "waterfall", "airplane", "bicycle",
		"candle", "diamond", "firework", "giraffe", "helicopter",
		"icecream", "jellyfish", "kangaroo", "lighthouse", "mushroom",
		"necklace", "parachute", "robot", "snowflake", "telescope",
		"unicorn", "violin", "windmill", "butterfly", "cactus",
		"dolphin", "eagle", "fountain", "globe", "harp"
	};

	public static String getRandomWord() {
		return WORDS[random.nextInt(WORDS.length)];
	}
}
