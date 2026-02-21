package com.project.drawguess.game;

import java.util.Random;

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

	public static String getRandomWord() {
		return WORDS[random.nextInt(WORDS.length)];
	}
}