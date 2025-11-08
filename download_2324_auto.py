#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
自動下載 2324.TW 仁寶股票數據並轉換為 DreamHouse Trading CSV 格式
"""

import yfinance as yf
import pandas as pd
from datetime import datetime

def download_stock_data(symbol, period='1y', interval='1d'):
    """下載股票數據"""
    print(f"正在下載 {symbol} 的數據...")
    print(f"期間: {period}, 間隔: {interval}")
    
    try:
        stock = yf.Ticker(symbol)
        df = stock.history(period=period, interval=interval)
        
        if df.empty:
            print(f"❌ 無法下載 {symbol} 的數據")
            return None
        
        print(f"✅ 成功下載 {len(df)} 筆數據")
        return df
        
    except Exception as e:
        print(f"❌ 下載失敗: {e}")
        return None

def convert_to_dreamhouse_format(df):
    """轉換為 DreamHouse Trading 格式"""
    if df is None or df.empty:
        return None
    
    df_converted = df.reset_index()
    
    # 找出時間列
    time_col = None
    for col in ['Date', 'Datetime', 'index']:
        if col in df_converted.columns:
            time_col = col
            break
    
    if time_col:
        df_converted = df_converted.rename(columns={time_col: 'Timestamp'})
    
    # 選擇需要的列
    required_columns = ['Timestamp', 'Open', 'High', 'Low', 'Close', 'Volume']
    df_result = df_converted[required_columns].copy()
    
    # 格式化時間戳
    df_result['Timestamp'] = pd.to_datetime(df_result['Timestamp'])
    df_result['Timestamp'] = df_result['Timestamp'].dt.strftime('%Y-%m-%d %H:%M:%S')
    
    # 格式化數值
    for col in ['Open', 'High', 'Low', 'Close']:
        df_result[col] = df_result[col].round(2)
    
    df_result['Volume'] = df_result['Volume'].astype(int)
    
    print(f"✅ 數據轉換完成")
    print(f"   數據範圍: {df_result['Timestamp'].iloc[0]} 至 {df_result['Timestamp'].iloc[-1]}")
    print(f"   總筆數: {len(df_result)}")
    
    return df_result

def save_to_csv(df, filename):
    """保存為 CSV 文件"""
    if df is None or df.empty:
        print("❌ 沒有數據可以保存")
        return False
    
    try:
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(f"# DreamHouse Trading - 2324.TW 仁寶股票數據\n")
            f.write(f"# 下載時間: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write(f"# 格式: Timestamp,Open,High,Low,Close,Volume\n")
        
        df.to_csv(filename, mode='a', index=False, encoding='utf-8')
        
        print(f"✅ 數據已保存至: {filename}")
        return True
        
    except Exception as e:
        print(f"❌ 保存失敗: {e}")
        return False

def main():
    """主程式 - 自動下載最近 1 年日線數據"""
    print("=" * 60)
    print("   2324.TW 仁寶股票數據下載工具 (自動模式)")
    print("=" * 60)
    print()
    
    symbol = "2457.TW"
    period = "1y"
    interval = "1d"
    filename = "2457_TW_飛宏_1年日線.csv"
    
    print(f"下載設定: {symbol}")
    print(f"期間: {period} (最近 1 年)")
    print(f"間隔: {interval} (日線)")
    print(f"輸出檔案: {filename}")
    print()
    
    # 下載數據
    df = download_stock_data(symbol, period=period, interval=interval)
    
    if df is None:
        print()
        print("=" * 60)
        print("❌ 下載失敗")
        print("=" * 60)
        return
    
    # 轉換格式
    df_converted = convert_to_dreamhouse_format(df)
    
    # 保存文件
    success = save_to_csv(df_converted, filename)
    
    print()
    print("=" * 60)
    if success:
        print("✅ 完成！")
        print()
        print(f"📁 文件路徑: {filename}")
        print(f"📊 數據筆數: {len(df_converted)}")
        print()
        print("您可以在 DreamHouse Trading 中匯入此文件:")
        print("   檔案 → 匯入 CSV... → 選擇此文件 → 勾選「包含標題行」→ 匯入")
    else:
        print("❌ 保存失敗")
    print("=" * 60)

if __name__ == "__main__":
    main()

