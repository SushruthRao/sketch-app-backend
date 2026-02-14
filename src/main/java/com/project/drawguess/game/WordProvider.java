package com.project.drawguess.game;

import java.util.Random;

public class WordProvider {

	private static final Random random = new Random();

	private static final String[] WORDS = { "tree", "cat", "guitar", "computer", "book", "car", "bird" };

	public static String getRandomWord() {
		return WORDS[random.nextInt(WORDS.length)];
	}
}
