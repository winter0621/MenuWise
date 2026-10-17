package com.example.careium.frontend.home.fragments;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AlertDialog;

import com.example.careium.R;
import com.example.careium.core.adapters.RecipesAdapter;
import com.example.careium.databinding.FragmentRecipesBinding;


public class RecipesFragment extends Fragment {

    public RecipesFragment() {
        // Required empty public constructor
    }

    public static RecipesFragment newInstance() {
        return new RecipesFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_recipes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        com.example.careium.databinding.FragmentRecipesBinding binding = FragmentRecipesBinding.bind(view);

        String[] imageText = {"培根奶油意面", "混合蔬菜沙拉", "清爽凉拌菜", "蛋炒饭配水波蛋",
                "牛肉汉堡", "松饼", "巧克力蛋糕", "白酱意面"};
        String[][] ingredients = {
                {"意大利面 100 克", "培根 40 克", "鸡蛋 1 个", "帕玛森芝士、黑胡椒适量"},
                {"生菜 80 克", "小番茄 6 颗", "黄瓜半根", "橄榄油、柠檬汁适量"},
                {"黄瓜 1 根", "胡萝卜半根", "蒜末少许", "醋、生抽、香油适量"},
                {"米饭 1 碗", "鸡蛋 2 个", "葱花少许", "盐和食用油适量"},
                {"牛肉饼 1 份", "汉堡胚 1 个", "生菜、番茄各适量", "芝士片 1 片"},
                {"面粉 100 克", "牛奶 100 毫升", "鸡蛋 1 个", "泡打粉和糖少许"},
                {"低筋面粉 100 克", "可可粉 20 克", "鸡蛋 2 个", "牛奶、糖适量"},
                {"意大利面 100 克", "牛奶 150 毫升", "黄油 10 克", "蘑菇和芝士适量"}
        };
        String[] steps = {
                "煮熟意面。小火加热培根，加入蛋液和芝士，再拌入意面；用少量煮面水调整浓度。",
                "洗净并切好蔬菜，混合橄榄油与柠檬汁，食用前淋在蔬菜上。",
                "黄瓜和胡萝卜切片，加入蒜末、醋、生抽和香油拌匀，冷藏 10 分钟更入味。",
                "鸡蛋炒至半熟后盛出，米饭炒散，加入鸡蛋和葱花调味。水波蛋另行煮至蛋白凝固后搭配。",
                "牛肉饼煎熟并融化芝士，依次夹入汉堡胚、生菜和番茄即可。",
                "面粉、牛奶、鸡蛋和泡打粉拌匀；小火将面糊两面煎至金黄。",
                "将粉类过筛，与打发的鸡蛋、牛奶混合，倒入模具后以 170℃ 烘烤约 25 分钟。",
                "煮熟意面。黄油炒香蘑菇，加入牛奶和芝士煮成白酱，再拌入意面。"
        };
        int[] images = {
                R.drawable.recipe_carbonara, R.drawable.recipe_mixed_salad,
                R.drawable.recipe_dry_salad, R.drawable.recipe_fried_rise_with_poached_egg,
                R.drawable.recipe_burger, R.drawable.recipe_pan_cake,
                R.drawable.recipe_chocolate_cake, R.drawable.recipe_pasta_with_white_sause};
        RecipesAdapter recipesAdapter = new RecipesAdapter(getContext(), imageText, images);
        binding.recipesGridview.setAdapter(recipesAdapter);
        binding.recipesGridview.setOnItemClickListener((parent, itemView, position, id) -> {
            String message = getString(R.string.recipe_ingredients_title) + "\n• "
                    + android.text.TextUtils.join("\n• ", ingredients[position]) + "\n\n"
                    + getString(R.string.recipe_steps_title) + "\n" + steps[position];
            new AlertDialog.Builder(requireContext()).setTitle(imageText[position]).setMessage(message)
                    .setPositiveButton("知道了", null).show();
        });
    }
}
