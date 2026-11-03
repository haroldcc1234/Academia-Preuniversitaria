import globals from 'globals';
import react from 'eslint-plugin-react';
export default [{ignores:['dist/**','node_modules/**']},{files:['src/**/*.{js,jsx}'],languageOptions:{ecmaVersion:'latest',sourceType:'module',parserOptions:{ecmaFeatures:{jsx:true}},globals:globals.browser},plugins:{react},settings:{react:{version:'18.3'}},rules:{'no-undef':'error','no-unreachable':'error','no-dupe-keys':'error','react/jsx-no-undef':'error'}}];
