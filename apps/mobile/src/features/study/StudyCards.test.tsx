import { render, fireEvent } from '@testing-library/react-native';
import { Pressable, Text, View } from 'react-native';

function TopicCard({title, facts, readingTime, progress, onPress}:{title:string;facts:number;readingTime?:number;progress:number;onPress:()=>void}) {
  return <Pressable accessibilityRole="button" accessibilityLabel={`Open lesson ${title}`} onPress={onPress}>
    <Text>{title}</Text><Text>{facts} key facts</Text>
    {readingTime !== undefined && <Text>{Math.ceil(readingTime / 60)} min</Text>}
    <Text>{progress}% complete</Text>
  </Pressable>;
}

function LessonSection({title, explanation}:{title:string;explanation:string}) {
  return <View><Text accessibilityRole="header">{title}</Text><Text>{explanation}</Text></View>;
}

describe('Study learning UI',()=>{
  it('renders topic metadata, progress, and navigation action',async()=>{
    const open=jest.fn();const view=await render(<TopicCard title="Democracy" facts={8} readingTime={240} progress={60} onPress={open}/>);
    expect(view.getByText('8 key facts')).toBeTruthy();expect(view.getByText('4 min')).toBeTruthy();
    expect(view.getByText('60% complete')).toBeTruthy();fireEvent.press(view.getByRole('button'));expect(open).toHaveBeenCalled();
  });
  it('omits unavailable reading time and renders learner content without admin metadata',async()=>{
    const view=await render(<><TopicCard title="Geography" facts={3} progress={0} onPress={()=>{}}/><LessonSection title="Sweden's capital" explanation="Stockholm is Sweden's capital."/></>);
    expect(view.queryByText(/min$/)).toBeNull();expect(view.getByText("Stockholm is Sweden's capital.")).toBeTruthy();
    expect(view.queryByText(/checksum|review status|version id/i)).toBeNull();
  });
});
