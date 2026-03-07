package com.project.drawguess.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class WordProvider {

	private static final Random random = new Random();

	private static final String[] WORDS = {
		"tree", "cat", "guitar", "computer", "book", "car", "bird",
		"house", "dog", "sun", "moon", "star", "cloud", "river", "mountain",
		"phone", "chair", "table", "window", "door", "shoe", "hat", "shirt",
		"pizza", "burger", "apple", "banana", "grape", "orange", "cake", "cookie",
		"train", "plane", "boat", "bicycle", "bus", "rocket", "bridge", "road",
		"flower", "grass", "forest", "beach", "island", "desert", "snow", "rain",
		"clock", "watch", "camera", "television", "radio", "keyboard", "mouse", "monitor",
		"pencil", "pen", "paper", "notebook", "backpack", "bottle", "cup", "plate",
		"spoon", "fork", "knife", "bed", "pillow", "blanket", "mirror", "lamp",
		"doctor", "teacher", "student", "police", "firefighter", "chef", "artist", "pilot",
		"lion", "tiger", "elephant", "monkey", "horse", "fish", "shark", "whale",
		"ball", "soccer", "basketball", "tennis", "golf", "baseball", "volleyball", "hockey",
		"robot", "alien", "ghost", "monster", "dragon", "wizard", "knight", "castle",
		"crown", "treasure", "map", "key"
	};

	/**
	 * Returns a random word that has not been used in the current session.
	 * Falls back to any random word if all words have been exhausted.
	 */
	public static String getRandomWord(Set<String> usedWords) {
		List<String> available = new ArrayList<>(Arrays.asList(WORDS));
		available.removeAll(usedWords);
		if (available.isEmpty()) {
			// All words exhausted — fall back to full list
			return WORDS[random.nextInt(WORDS.length)];
		}
		return available.get(random.nextInt(available.size()));
	}
}