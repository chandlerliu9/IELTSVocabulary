"""Convert the source workbook and audio directory into APK-ready assets."""
from pathlib import Path
import json, shutil, openpyxl

ROOT=Path(__file__).resolve().parents[1]
BOOK=ROOT/'ielts_9400_master - 全量文件.xlsx'
OUT=ROOT/'app/src/main/assets'
OUT.mkdir(parents=True,exist_ok=True)
sheet=openpyxl.load_workbook(BOOK,read_only=True,data_only=True).active
headers=[c.value for c in next(sheet.iter_rows())]
idx={v:i for i,v in enumerate(headers)}
rows=[]; audio=set()
for values in sheet.iter_rows(min_row=2, values_only=True):
    if not values[idx['enabled']]: continue
    def val(name): return str(values[idx[name]] or '')
    rows.append([int(values[idx['order']]),val('term'),val('phonetic_ipa'),val('part_of_speech'),val('definition_zh'),val('definition_en'),val('example_en'),val('example_zh'),val('audio_file')])
    if val('audio_file'): audio.add(Path(val('audio_file')).name)
(OUT/'words.json').write_text(json.dumps(rows,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
audio_out=OUT/'audio'; audio_out.mkdir(exist_ok=True)
for i,name in enumerate(audio,1):
    src=ROOT/'audio/uk'/name
    if src.exists(): shutil.copy2(src,audio_out/name)
print(f'Exported {len(rows)} words and {len(list(audio_out.glob("*.mp3")))} audio files')
